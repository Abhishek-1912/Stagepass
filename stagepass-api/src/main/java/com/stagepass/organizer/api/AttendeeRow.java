package com.stagepass.organizer.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One ticket of the organizer's event, with who holds it and whether it was used. */
public record AttendeeRow(UUID ticketId, String ticketCode, String ticketType, String holderName, String bookingCode,
        String attendeeEmail, String attendeePhone, String status, OffsetDateTime bookedAt, OffsetDateTime checkedInAt) {
}
