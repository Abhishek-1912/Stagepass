package com.stagepass.booking.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A refund on one of the organizer's events, for the approval queue. */
public record OrganizerRefundView(UUID id, UUID bookingId, String bookingCode, String attendeeName,
        String attendeeEmail, String kind, String status, int ticketCount, long amountPaise, String reason,
        String note, OffsetDateTime createdAt) {
}
