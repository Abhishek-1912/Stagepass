package com.stagepass.notification.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationView(UUID id, String type, String title, String body, String link, OffsetDateTime createdAt,
        boolean read) {
}
