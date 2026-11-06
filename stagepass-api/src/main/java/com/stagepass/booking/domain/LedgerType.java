package com.stagepass.booking.domain;

/** Organizer ledger entry kinds; amounts are signed paise (credit to organizer positive). */
public enum LedgerType {
    SALE,
    REFUND,
    COMMISSION,
    PAYOUT,
    ADJUSTMENT,
    TDS
}
