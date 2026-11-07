package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PayoutRow(UUID id, long amountPaise, String reference, OffsetDateTime paidAt, String recordedByEmail,
        String note) {
}
