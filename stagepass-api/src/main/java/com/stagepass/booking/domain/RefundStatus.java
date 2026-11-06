package com.stagepass.booking.domain;

public enum RefundStatus {
    REQUESTED,
    PENDING_APPROVAL,
    DECLINED,
    PROCESSING,
    PROCESSED,
    FAILED
}
