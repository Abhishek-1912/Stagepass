package com.stagepass.notification.service;

/** Transactional emails sent by the API (auth emails are sent by Supabase). Stored by name in email_outbox. */
public enum EmailTemplate {
    WELCOME,
    ORGANIZER_APPROVED,
    ORGANIZER_REJECTED,
    EVENT_APPROVED,
    EVENT_REJECTED,
    BOOKING_CONFIRMED,
    BOOKING_EXPIRED,
    REFUND_PROCESSED,
    REFUND_DECLINED,
    EVENT_CANCELLED,
    EVENT_CHANGED,
    STAFF_INVITED
}
