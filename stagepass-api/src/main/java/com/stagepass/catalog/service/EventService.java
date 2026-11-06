package com.stagepass.catalog.service;

import com.stagepass.catalog.api.EventResponse;
import com.stagepass.catalog.api.EventSummaryResponse;
import com.stagepass.catalog.api.EventUpsertRequest;
import com.stagepass.catalog.domain.Category;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.RefundPolicy;
import com.stagepass.catalog.repository.CategoryRepository;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.Slugs;
import com.stagepass.identity.repository.OrganizerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Organizer-side event drafts and edits (spec §8.1, §8.4a). Other organizers' events answer 404. */
@Service
public class EventService {

    static final int RESCHEDULE_REFUND_DAYS = 7;

    private final EventRepository events;
    private final CategoryRepository categories;
    private final OrganizerRepository organizers;
    private final VenueService venues;
    private final AuditLogService audit;
    private final EventImagePolicy images;

    public EventService(EventRepository events, CategoryRepository categories, OrganizerRepository organizers,
            VenueService venues, AuditLogService audit, EventImagePolicy images) {
        this.events = events;
        this.categories = categories;
        this.organizers = organizers;
        this.venues = venues;
        this.audit = audit;
        this.images = images;
    }

    @Transactional
    public EventResponse create(UUID organizerId, UUID actorId, EventUpsertRequest r) {
        FieldErrors errors = FieldErrors.start()
                .require(r.title(), "title").require(r.categoryId(), "categoryId")
                .require(r.startsAt(), "startsAt").require(r.endsAt(), "endsAt");
        errors.throwIfAny();
        Event event = new Event();
        event.setOrganizer(organizers.getReferenceById(organizerId));
        event.setSlug(Slugs.unique(Slugs.slugify(r.title())));
        apply(organizerId, event, r);
        events.saveAndFlush(event);
        audit.record(actorId, "EVENT_CREATED", "event", event.getId().toString(), null, Map.of("title", event.getTitle()));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> list(UUID organizerId, EventStatus status, Pageable pageable) {
        var page = status == null ? events.findByOrganizerId(organizerId, pageable)
                : events.findByOrganizerIdAndStatus(organizerId, status, pageable);
        return PageResponse.of(page, EventSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID organizerId, UUID eventId) {
        return EventResponse.from(owned(organizerId, eventId));
    }

    @Transactional
    public EventResponse update(UUID organizerId, UUID actorId, UUID eventId, EventUpsertRequest r) {
        Event event = owned(organizerId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Cancelled or completed events can't be edited.");
        }
        OffsetDateTime oldStart = event.getStartsAt();
        OffsetDateTime oldEnd = event.getEndsAt();
        UUID oldVenue = event.getVenue() == null ? null : event.getVenue().getId();

        apply(organizerId, event, r);

        if (event.getStatus() == EventStatus.PENDING_REVIEW) {
            event.setStatus(EventStatus.DRAFT);
            event.setSubmittedAt(null);
        }
        UUID newVenue = event.getVenue() == null ? null : event.getVenue().getId();
        boolean rescheduled = !event.getStartsAt().isEqual(oldStart) || !event.getEndsAt().isEqual(oldEnd)
                || !Objects.equals(oldVenue, newVenue);
        if (event.getStatus() == EventStatus.PUBLISHED && rescheduled) {
            event.setRescheduleRefundUntil(OffsetDateTime.now().plusDays(RESCHEDULE_REFUND_DAYS));
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("startsAt", oldStart.toString());
            before.put("endsAt", oldEnd.toString());
            before.put("venueId", oldVenue == null ? null : oldVenue.toString());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("startsAt", event.getStartsAt().toString());
            after.put("endsAt", event.getEndsAt().toString());
            after.put("venueId", newVenue == null ? null : newVenue.toString());
            audit.record(actorId, "EVENT_RESCHEDULED", "event", eventId.toString(), before, after);
        }
        events.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public void delete(UUID organizerId, UUID actorId, UUID eventId) {
        Event event = owned(organizerId, eventId);
        boolean deletableStatus = event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.REJECTED;
        if (!deletableStatus || events.hasBookings(eventId)) {
            throw new ApiException(ErrorCode.INVALID_STATE,
                    "Only drafts or rejected events without bookings can be deleted. Cancel the event instead.");
        }
        events.delete(event);
        audit.record(actorId, "EVENT_DELETED", "event", eventId.toString(), Map.of("title", event.getTitle()), null);
    }

    /** The organizer's event or 404. */
    @Transactional(readOnly = true)
    public Event owned(UUID organizerId, UUID eventId) {
        return events.findByIdAndOrganizerId(eventId, organizerId).orElseThrow(() -> ApiException.notFound("Event"));
    }

    private void apply(UUID organizerId, Event e, EventUpsertRequest r) {
        FieldErrors errors = FieldErrors.start();
        if (r.title() != null) {
            e.setTitle(r.title());
        }
        if (r.summary() != null) {
            e.setSummary(r.summary());
        }
        if (r.description() != null) {
            e.setDescription(r.description());
        }
        if (r.categoryId() != null) {
            Category category = categories.findById(r.categoryId()).filter(Category::isActive).orElse(null);
            if (category == null) {
                errors.add("categoryId", "Choose an active category");
            } else {
                e.setCategory(category);
            }
        }
        if (r.online() != null) {
            e.setOnline(r.online());
            if (r.online()) {
                e.setVenue(null);
            }
        }
        if (r.venueId() != null) {
            e.setVenue(venues.owned(organizerId, r.venueId()));
            e.setOnline(false);
        }
        if (r.onlineUrl() != null) {
            e.setOnlineUrl(r.onlineUrl());
        }
        if (r.startsAt() != null) {
            e.setStartsAt(r.startsAt());
        }
        if (r.endsAt() != null) {
            e.setEndsAt(r.endsAt());
        }
        if (e.getStartsAt() != null && e.getEndsAt() != null && !e.getEndsAt().isAfter(e.getStartsAt())) {
            errors.add("endsAt", "End time must be after the start time");
        }
        if (r.terms() != null) {
            e.setTerms(r.terms());
        }
        if (r.maxTicketsPerUser() != null) {
            e.setMaxTicketsPerUser(r.maxTicketsPerUser());
        }
        if (r.refundPolicy() != null) {
            try {
                e.setRefundPolicy(RefundPolicy.valueOf(r.refundPolicy()));
            } catch (IllegalArgumentException ex) {
                errors.add("refundPolicy", "must be NONE or UNTIL_HOURS_BEFORE");
            }
        }
        if (r.refundCutoffHours() != null) {
            e.setRefundCutoffHours(r.refundCutoffHours());
        }
        if (r.coverImageUrl() != null && e.getId() != null) {
            images.check(e.getId(), r.coverImageUrl(), "coverImageUrl", errors);
            e.setCoverImageUrl(r.coverImageUrl());
        }
        if (r.gallery() != null && e.getId() != null) {
            r.gallery().forEach(url -> images.check(e.getId(), url, "gallery", errors));
            e.setGallery(new ArrayList<>(r.gallery()));
        }
        errors.throwIfAny();
    }
}
