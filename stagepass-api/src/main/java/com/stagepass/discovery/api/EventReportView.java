package com.stagepass.discovery.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/** What the reporter (or an admin action) gets back about one report. */
public record EventReportView(UUID id, UUID eventId, String reason, String details, String status, String resolutionNote,
        OffsetDateTime createdAt) {
}
