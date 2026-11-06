package com.stagepass.discovery.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An event as shown on public cards (home, explore, organizer profile). */
public record PublicEventCard(UUID id, String slug, String title, String summary, String coverImageUrl,
        OffsetDateTime startsAt, OffsetDateTime endsAt, boolean online, String venueName, String city,
        String categorySlug, String categoryName, Long minPricePaise, boolean soldOut, boolean featured,
        Double distanceKm) {
}
