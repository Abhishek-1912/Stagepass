package com.stagepass.admin.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizerPayoutSummary(UUID organizerId, String orgName, String status, long pendingPaise, long payablePaise,
        long paidOutPaise, boolean negative, OffsetDateTime lastPayoutAt) {
}
