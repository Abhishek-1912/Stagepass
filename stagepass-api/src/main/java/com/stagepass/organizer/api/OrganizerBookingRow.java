package com.stagepass.organizer.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A booking on one of the organizer's events. */
public record OrganizerBookingRow(UUID id, String code, String status, String attendeeName, String attendeeEmail,
        String attendeePhone, int ticketCount, long totalPaise, long subtotalPaise, OffsetDateTime createdAt,
        OffsetDateTime confirmedAt) {
}
