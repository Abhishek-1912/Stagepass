package com.stagepass.catalog.domain;

public enum RefundPolicy {
    /** No attendee-requested refunds (event cancellation and rescheduling still refund). */
    NONE,
    /** Attendees may refund until {@code refundCutoffHours} before the start. */
    UNTIL_HOURS_BEFORE
}
