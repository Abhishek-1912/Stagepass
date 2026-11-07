package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminDisputeRow(UUID id, String razorpayDisputeId, UUID bookingId, String bookingCode, String eventTitle,
        long amountPaise, String reason, String status, OffsetDateTime createdAt) {
}
