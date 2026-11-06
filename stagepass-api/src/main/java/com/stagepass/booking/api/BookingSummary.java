package com.stagepass.booking.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of My bookings. */
public record BookingSummary(UUID id, String code, String status, String eventTitle, String eventSlug,
        String coverImageUrl, OffsetDateTime eventStartsAt, String city, int ticketCount, long totalPaise) {
}
