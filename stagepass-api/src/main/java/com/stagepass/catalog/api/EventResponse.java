package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.Event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Full event as its organizer or an admin sees it. */
public record EventResponse(UUID id, String slug, String title, String summary, String description, UUID categoryId,
        String categoryName, VenueResponse venue, boolean online, String onlineUrl, OffsetDateTime startsAt,
        OffsetDateTime endsAt, String timezone, String coverImageUrl, List<String> gallery, String terms,
        Integer maxTicketsPerUser, String refundPolicy, Integer refundCutoffHours, String status,
        String rejectionReason, boolean featured, OffsetDateTime rescheduleRefundUntil, OffsetDateTime submittedAt,
        OffsetDateTime publishedAt, UUID organizerId, String organizerName, List<TicketTypeResponse> ticketTypes) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getSlug(), e.getTitle(), e.getSummary(), e.getDescription(),
                e.getCategory().getId(), e.getCategory().getName(), VenueResponse.from(e.getVenue()), e.isOnline(),
                e.getOnlineUrl(), e.getStartsAt(), e.getEndsAt(), e.getTimezone(), e.getCoverImageUrl(),
                List.copyOf(e.getGallery()), e.getTerms(), e.getMaxTicketsPerUser(), e.getRefundPolicy().name(),
                e.getRefundCutoffHours(), e.getStatus().name(), e.getRejectionReason(), e.isFeatured(),
                e.getRescheduleRefundUntil(), e.getSubmittedAt(), e.getPublishedAt(), e.getOrganizer().getId(),
                e.getOrganizer().getOrgName(), e.getTicketTypes().stream().map(TicketTypeResponse::from).toList());
    }
}
