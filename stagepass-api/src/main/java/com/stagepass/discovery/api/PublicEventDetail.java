package com.stagepass.discovery.api;

import com.stagepass.catalog.api.VenueResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The public event page. Never includes the online link (ticket holders get it with their ticket). */
public record PublicEventDetail(UUID id, String slug, String title, String summary, String description,
        String coverImageUrl, List<String> gallery, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone,
        String status, boolean online, VenueResponse venue, String categorySlug, String categoryName, String terms,
        Integer maxTicketsPerUser, String refundPolicy, Integer refundCutoffHours, boolean soldOut,
        Long minPricePaise, List<PublicTicketType> ticketTypes, PublicOrganizer organizer,
        RatingSummary rating) {
}
