package com.stagepass.catalog.service;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.common.audit.AuditLogService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Spec §8.4a: when a live event's date, time or venue (including the venue's address) changes, ticket holders may
 * self-refund in full for 7 days. Plan 4 adds the emails to ticket holders here.
 */
@Component
public class RescheduleWindow {

    static final int DAYS = 7;

    private final AuditLogService audit;

    public RescheduleWindow(AuditLogService audit) {
        this.audit = audit;
    }

    public void openIfLive(Event event, UUID actorId, Map<String, Object> before, Map<String, Object> after) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return;
        }
        event.setRescheduleRefundUntil(OffsetDateTime.now().plusDays(DAYS));
        audit.record(actorId, "EVENT_RESCHEDULED", "event", event.getId().toString(), before, after);
    }
}
