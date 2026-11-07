package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPaymentRow(UUID id, UUID bookingId, String bookingCode, String eventTitle, String attendeeEmail,
        long amountPaise, String status, String method, String razorpayPaymentId, Long gatewayFeePaise,
        OffsetDateTime createdAt) {
}
