package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminReportRow(UUID id, UUID eventId, String eventTitle, String eventSlug, String organizerName,
        String reason, String details, String reporterEmail, String status, String resolutionNote,
        OffsetDateTime createdAt, int openReportsForEvent) {
}
