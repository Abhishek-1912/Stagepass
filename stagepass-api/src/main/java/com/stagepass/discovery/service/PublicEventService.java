package com.stagepass.discovery.service;

import com.stagepass.catalog.api.VenueResponse;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.TicketTypeStatus;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.error.ApiException;
import com.stagepass.discovery.api.EventSearchParams;
import com.stagepass.discovery.api.PublicEventDetail;
import com.stagepass.discovery.api.PublicOrganizer;
import com.stagepass.discovery.api.PublicOrganizerProfile;
import com.stagepass.discovery.api.PublicTicketType;
import com.stagepass.discovery.repository.PublicEventQueries;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.repository.OrganizerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Public event pages and organizer profiles. Unpublished events answer 404. */
@Service
public class PublicEventService {

    /** Statuses whose page stays reachable by slug (cancelled/completed keep ticket holders' links working). */
    static final Set<EventStatus> VISIBLE = EnumSet.of(EventStatus.PUBLISHED, EventStatus.CANCELLED, EventStatus.COMPLETED);

    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final PublicEventQueries queries;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PublicEventService(EventRepository events, OrganizerRepository organizers, PublicEventQueries queries,
            JdbcTemplate jdbc, Clock clock) {
        this.events = events;
        this.organizers = organizers;
        this.queries = queries;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicEventDetail detail(String slug) {
        Event e = findVisible(slug);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<PublicTicketType> types = e.getTicketTypes().stream()
                .filter(t -> t.getStatus() != TicketTypeStatus.HIDDEN)
                .map(t -> PublicTicketType.from(t, now))
                .toList();
        Long minPrice = e.getTicketTypes().stream()
                .filter(t -> t.getStatus() == TicketTypeStatus.ACTIVE)
                .map(t -> t.getPricePaise()).min(Long::compare).orElse(null);
        boolean soldOut = types.stream().noneMatch(t -> "ON_SALE".equals(t.availability())
                || "NOT_STARTED".equals(t.availability()));
        return new PublicEventDetail(e.getId(), e.getSlug(), e.getTitle(), e.getSummary(), e.getDescription(),
                e.getCoverImageUrl(), List.copyOf(e.getGallery()), e.getStartsAt(), e.getEndsAt(), e.getTimezone(),
                e.getStatus().name(), e.isOnline(), VenueResponse.from(e.getVenue()), e.getCategory().getSlug(),
                e.getCategory().getName(), e.getTerms(), e.getMaxTicketsPerUser(), e.getRefundPolicy().name(),
                e.getRefundCutoffHours(), soldOut, minPrice, types, PublicOrganizer.from(e.getOrganizer()));
    }

    /** For the share page: the visible event or 404. */
    @Transactional(readOnly = true)
    public Event findVisible(String slug) {
        Event e = events.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Event"));
        if (!VISIBLE.contains(e.getStatus()) || e.getOrganizer().getStatus() == OrganizerStatus.SUSPENDED) {
            throw ApiException.notFound("Event");
        }
        return e;
    }

    @Transactional(readOnly = true)
    public PublicOrganizerProfile organizer(String slug) {
        var o = organizers.findBySlug(slug)
                .filter(x -> x.getStatus() == OrganizerStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Organizer"));
        var upcoming = queries.search(EventSearchParams.of(EventSearchParams.Sort.SOONEST).withOrganizer(o.getId()),
                null, PageRequest.of(0, 24)).getContent();
        Integer past = jdbc.queryForObject("""
                select count(*) from events
                where organizer_id = ? and (status = 'COMPLETED' or (status = 'PUBLISHED' and ends_at <= now()))
                """, Integer.class, o.getId());
        return new PublicOrganizerProfile(PublicOrganizer.from(o), upcoming, past == null ? 0 : past);
    }
}
