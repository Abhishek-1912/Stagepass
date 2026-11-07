package com.stagepass.checkin.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A door staff invite; {@code name} appears once the person has signed in, {@code eventId} null = all events. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffResponse(UUID id, String email, String name, UUID eventId, String eventTitle, String status,
        OffsetDateTime createdAt, OffsetDateTime acceptedAt) {
}
