package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.Event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row in the admin event review queue. */
public record AdminEventSummary(UUID id, String title, String organizerName, String status, OffsetDateTime startsAt,
        OffsetDateTime submittedAt, String city, String coverImageUrl, boolean featured, boolean online) {

    public static AdminEventSummary from(Event e) {
        return new AdminEventSummary(e.getId(), e.getTitle(), e.getOrganizer().getOrgName(), e.getStatus().name(),
                e.getStartsAt(), e.getSubmittedAt(), e.getVenue() == null ? null : e.getVenue().getCity(),
                e.getCoverImageUrl(), e.isFeatured(), e.isOnline());
    }
}
