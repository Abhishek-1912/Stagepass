package com.stagepass.checkin.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Invite door staff by email to one event ({@code eventId}) or to all of the organizer's events (null). */
public record StaffRequest(@NotBlank @Email @Size(max = 254) String email, UUID eventId) {
}
