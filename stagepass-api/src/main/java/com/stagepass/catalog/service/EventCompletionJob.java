package com.stagepass.catalog.service;

import com.stagepass.common.audit.AuditLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spec §8.1: PUBLISHED → COMPLETED once an event has ended (runs every 15 minutes). */
@Component
public class EventCompletionJob {

    private final JdbcTemplate jdbc;
    private final AuditLogService audit;

    public EventCompletionJob(JdbcTemplate jdbc, AuditLogService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Scheduled(cron = "0 */15 * * * *")
    void scheduled() {
        completeEnded();
    }

    /** Returns how many events were completed. */
    @Transactional
    public int completeEnded() {
        List<UUID> ids = jdbc.queryForList("""
                update events set status = 'COMPLETED', is_featured = false
                where status = 'PUBLISHED' and ends_at < now()
                returning id
                """, UUID.class);
        ids.forEach(id -> audit.record(null, "EVENT_COMPLETED", "event", id.toString(), Map.of("status", "PUBLISHED"),
                Map.of("status", "COMPLETED")));
        return ids.size();
    }
}
