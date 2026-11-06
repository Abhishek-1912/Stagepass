package com.stagepass.organizer.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.organizer.service.PayoutBalances.Balances;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The organizer's balances, ledger entries (newest first) and payouts received. */
public record LedgerResponse(Balances balances, PageResponse<LedgerLine> entries, List<PayoutLine> payouts) {

    public record LedgerLine(UUID id, String type, long amountPaise, UUID eventId, String eventTitle, String bookingCode,
            String note, OffsetDateTime createdAt) {
    }

    public record PayoutLine(UUID id, long amountPaise, String reference, OffsetDateTime paidAt, String note) {
    }
}
