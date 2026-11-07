package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminOrganizerRow(UUID id, String orgName, String slug, String contactEmail, String status, int eventsCount,
        long payablePaise, boolean negative, OffsetDateTime appliedAt) {
}
