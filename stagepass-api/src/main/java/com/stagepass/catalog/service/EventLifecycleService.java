package com.stagepass.catalog.service;

import com.stagepass.catalog.api.AdminEventSummary;
import com.stagepass.catalog.api.EventResponse;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.TicketTypeStatus;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Event state transitions (spec §8.1): submit, admin approve/reject/feature, cancel. */
@Service
public class EventLifecycleService {

    private static final Set<EventStatus> SUBMITTABLE = EnumSet.of(EventStatus.DRAFT, EventStatus.REJECTED);
    private static final Set<EventStatus> CANCELLABLE =
            EnumSet.of(EventStatus.DRAFT, EventStatus.PENDING_REVIEW, EventStatus.PUBLISHED, EventStatus.REJECTED);

    private final EventService events;
    private final EventRepository repository;
    private final AuditLogService audit;
    private final EmailOutbox outbox;
    private final List<EventCancellationHandler> cancellationHandlers;

    public EventLifecycleService(EventService events, EventRepository repository, AuditLogService audit,
            EmailOutbox outbox, List<EventCancellationHandler> cancellationHandlers) {
        this.cancellationHandlers = cancellationHandlers;
        this.events = events;
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public EventResponse submit(UUID organizerId, UUID actorId, UUID eventId) {
        Event e = events.owned(organizerId, eventId);
        if (!SUBMITTABLE.contains(e.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only drafts or rejected events can be submitted.");
        }
        requireReadyToPublish(e);
        EventStatus before = e.getStatus();
        e.setStatus(EventStatus.PENDING_REVIEW);
        e.setSubmittedAt(OffsetDateTime.now());
        e.setRejectionReason(null);
        audit.record(actorId, "EVENT_SUBMITTED", "event", eventId.toString(), Map.of("status", before.name()),
                Map.of("status", EventStatus.PENDING_REVIEW.name()));
        return EventResponse.from(e);
    }

    @Transactional
    public EventResponse cancel(UUID organizerId, UUID actorId, UUID eventId, String reason) {
        Event e = events.owned(organizerId, eventId);
        if (!CANCELLABLE.contains(e.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "This event can no longer be cancelled.");
        }
        EventStatus before = e.getStatus();
        e.setStatus(EventStatus.CANCELLED);
        e.setFeatured(false);
        audit.record(actorId, "EVENT_CANCELLED", "event", eventId.toString(), Map.of("status", before.name()),
                Map.of("status", EventStatus.CANCELLED.name(), "reason", reason));
        if (before == EventStatus.PUBLISHED) {
            cancellationHandlers.forEach(h -> h.onEventCancelled(eventId, actorId));
        }
        return EventResponse.from(e);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminEventSummary> adminList(EventStatus status, Pageable pageable) {
        return PageResponse.of(repository.findByStatus(status, pageable), AdminEventSummary::from);
    }

    @Transactional(readOnly = true)
    public EventResponse adminGet(UUID eventId) {
        return EventResponse.from(find(eventId));
    }

    @Transactional
    public EventResponse approve(UUID adminId, UUID eventId) {
        Event e = find(eventId);
        if (e.getStatus() != EventStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only events waiting for review can be approved.");
        }
        if (!e.getStartsAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "This event has already started; reject it instead.");
        }
        requireReadyToPublish(e);
        e.setStatus(EventStatus.PUBLISHED);
        e.setPublishedAt(OffsetDateTime.now());
        e.setReviewedBy(adminId);
        audit.record(adminId, "EVENT_APPROVED", "event", eventId.toString(), Map.of("status", "PENDING_REVIEW"),
                Map.of("status", "PUBLISHED"));
        outbox.enqueue(e.getOrganizer().getContactEmail(), EmailTemplate.EVENT_APPROVED,
                Map.of("title", e.getTitle(), "slug", e.getSlug()));
        return EventResponse.from(e);
    }

    @Transactional
    public EventResponse reject(UUID adminId, UUID eventId, String reason) {
        Event e = find(eventId);
        if (e.getStatus() != EventStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only events waiting for review can be rejected.");
        }
        e.setStatus(EventStatus.REJECTED);
        e.setRejectionReason(reason);
        e.setReviewedBy(adminId);
        audit.record(adminId, "EVENT_REJECTED", "event", eventId.toString(), Map.of("status", "PENDING_REVIEW"),
                Map.of("status", "REJECTED", "reason", reason));
        outbox.enqueue(e.getOrganizer().getContactEmail(), EmailTemplate.EVENT_REJECTED,
                Map.of("title", e.getTitle(), "reason", reason, "eventId", eventId.toString()));
        return EventResponse.from(e);
    }

    @Transactional
    public EventResponse feature(UUID adminId, UUID eventId, boolean featured) {
        Event e = find(eventId);
        if (e.getStatus() != EventStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only published events can be featured.");
        }
        e.setFeatured(featured);
        audit.record(adminId, "EVENT_FEATURED", "event", eventId.toString(), null, Map.of("featured", featured));
        return EventResponse.from(e);
    }

    /** The submit checks, also re-run at approval so nothing unreviewed or incomplete gets published. */
    static void requireReadyToPublish(Event e) {
        boolean hasActiveTickets = e.getTicketTypes().stream().anyMatch(t -> t.getStatus() == TicketTypeStatus.ACTIVE);
        FieldErrors.start()
                .addIf(e.getSummary() == null || e.getSummary().isBlank(), "summary", "Add a short summary")
                .addIf(e.getCoverImageUrl() == null, "coverImageUrl", "Add a cover image")
                .addIf(!hasActiveTickets, "ticketTypes", "Add at least one ticket type that's on sale")
                .addIf(!e.getStartsAt().isAfter(OffsetDateTime.now()), "startsAt", "Start time must be in the future")
                .addIf(!e.isOnline() && e.getVenue() == null, "venueId", "Choose a venue or mark the event as online")
                .addIf(e.isOnline() && (e.getOnlineUrl() == null || e.getOnlineUrl().isBlank()), "onlineUrl",
                        "Add the online event link")
                .throwIfAny();
    }

    private Event find(UUID eventId) {
        return repository.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
    }
}
