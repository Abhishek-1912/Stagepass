package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRefundRow(UUID id, UUID bookingId, String bookingCode, String eventTitle, String kind, String status,
        long amountPaise, int attempts, boolean needsRetry, String failureReason, OffsetDateTime createdAt) {
}
