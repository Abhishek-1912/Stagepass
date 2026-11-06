package com.stagepass.notification.service;

/** Transactional emails sent by the API (auth emails are sent by Supabase). Stored by name in email_outbox. */
public enum EmailTemplate {
    WELCOME,
    ORGANIZER_APPROVED,
    ORGANIZER_REJECTED,
    EVENT_APPROVED,
    EVENT_REJECTED
}
