package com.stagepass.checkin.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * One scan at the door: the QR token, or the ticket code typed in by hand (exactly one of the two). No length limits:
 * anything a camera reads must come back as a red INVALID screen, never an error.
 */
public record ScanRequest(@NotNull UUID eventId, UUID gateId, String token, String code) {
}
