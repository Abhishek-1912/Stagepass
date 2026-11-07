package com.stagepass.admin.api;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditRow(UUID id, OffsetDateTime at, String actorEmail, String action, String entityType, String entityId,
        JsonNode before, JsonNode after) {
}
