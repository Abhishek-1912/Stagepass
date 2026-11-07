package com.stagepass.admin.api;

import com.stagepass.organizer.service.PayoutBalances;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Everything an admin needs about one organizer. The bank account is masked. */
public record AdminOrganizerDetail(UUID id, UUID userId, String orgName, String slug, String status,
        String contactEmail, String contactPhone, String website, String pan, String gstin, String state,
        String payoutAccountName, String payoutAccountMasked, String payoutIfsc, Integer commissionBpsOverride,
        OffsetDateTime appliedAt, OffsetDateTime reviewedAt, PayoutBalances.Balances balances, List<PayoutRow> payouts) {
}
