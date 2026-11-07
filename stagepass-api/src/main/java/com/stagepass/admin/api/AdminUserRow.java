package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserRow(UUID id, String email, String fullName, List<String> roles, String status,
        OffsetDateTime createdAt, OffsetDateTime suspendedAt, String suspensionReason) {
}
