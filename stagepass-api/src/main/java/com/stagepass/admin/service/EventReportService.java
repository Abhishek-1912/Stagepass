package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminReportRow;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.discovery.api.EventReportRequest;
import com.stagepass.discovery.api.EventReportView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Attendee reports of an event, and the admin queue that resolves or dismisses them (spec 9, 18). */
@Service
public class EventReportService {

    public static final List<String> REASONS = List.of("SCAM", "MISLEADING", "OFFENSIVE", "DUPLICATE", "OTHER");
    private static final List<String> STATUSES = List.of("OPEN", "RESOLVED", "DISMISSED");

    public record Reported(EventReportView report, boolean created) {
    }

    private static final RowMapper<EventReportView> VIEW = (rs, n) -> new EventReportView(rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class), rs.getString("reason"), rs.getString("details"), rs.getString("status"),
            rs.getString("resolution_note"), rs.getObject("created_at", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;

    public EventReportService(NamedParameterJdbcTemplate sql, AuditLogService audit) {
        this.sql = sql;
        this.audit = audit;
    }

    /** One open report per user per event: a repeat answers the open one. Only events visible in discovery. */
    @Transactional
    public Reported report(UUID userId, UUID eventId, EventReportRequest r) {
        String reason = AdminQueries.oneOf(REASONS, r.reason(), "reason");
        String details = r.details() == null || r.details().isBlank() ? null : r.details().strip();
        if ("OTHER".equals(reason) && details == null) {
            throw FieldErrors.single("details", "tell us what is wrong with this event");
        }
        Boolean visible = sql.queryForObject("""
                select exists (select 1 from events e join organizers o on o.id = e.organizer_id
                               where e.id = :event and e.status in ('PUBLISHED', 'CANCELLED', 'COMPLETED')
                                 and o.status <> 'SUSPENDED')
                """, new MapSqlParameterSource("event", eventId), Boolean.class);
        if (!Boolean.TRUE.equals(visible)) {
            throw ApiException.notFound("Event");
        }
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId).addValue("user", userId)
                .addValue("reason", reason).addValue("details", details);
        List<EventReportView> inserted = sql.query("""
                insert into event_reports (event_id, reporter_id, reason, details) values (:event, :user, :reason, :details)
                on conflict (event_id, reporter_id) where status = 'OPEN' do nothing
                returning id, event_id, reason, details, status, resolution_note, created_at
                """, p, VIEW);
        if (!inserted.isEmpty()) {
            return new Reported(inserted.get(0), true);
        }
        return new Reported(sql.queryForObject("""
                select id, event_id, reason, details, status, resolution_note, created_at from event_reports
                where event_id = :event and reporter_id = :user and status = 'OPEN'
                """, p, VIEW), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReportRow> list(String status, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder("""
                from event_reports r join events e on e.id = r.event_id join organizers o on o.id = e.organizer_id
                join users u on u.id = r.reporter_id where true""");
        if (AdminQueries.present(status)) {
            p.addValue("status", AdminQueries.oneOf(STATUSES, status, "status"));
            body.append(" and r.status = :status");
        }
        return AdminQueries.page(sql, """
                select r.id, r.event_id, e.title, e.slug, o.org_name, r.reason, r.details, u.email, r.status,
                       r.resolution_note, r.created_at,
                       (select count(*) from event_reports x where x.event_id = r.event_id and x.status = 'OPEN') as open_count
                """, body.toString(), "r.created_at desc, r.id", p, page, size, (rs, n) -> new AdminReportRow(
                rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class), rs.getString("title"),
                rs.getString("slug"), rs.getString("org_name"), rs.getString("reason"), rs.getString("details"),
                rs.getString("email"), rs.getString("status"), rs.getString("resolution_note"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getInt("open_count")));
    }

    @Transactional
    public EventReportView resolve(UUID adminId, UUID reportId, String note) {
        return decide(adminId, reportId, note, "RESOLVED", "REPORT_RESOLVED");
    }

    @Transactional
    public EventReportView dismiss(UUID adminId, UUID reportId, String note) {
        return decide(adminId, reportId, note, "DISMISSED", "REPORT_DISMISSED");
    }

    private EventReportView decide(UUID adminId, UUID reportId, String note, String status, String action) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", reportId);
        Optional<String> current = sql.queryForList("select status from event_reports where id = :id for update", p,
                String.class).stream().findFirst();
        if (current.isEmpty()) {
            throw ApiException.notFound("Report");
        }
        if (!"OPEN".equals(current.get())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "This report was already " + current.get().toLowerCase() + ".");
        }
        String n = note.strip();
        EventReportView v = sql.queryForObject("""
                update event_reports set status = :status, resolution_note = :note, resolved_by = :admin where id = :id
                returning id, event_id, reason, details, status, resolution_note, created_at
                """, p.addValue("status", status).addValue("note", n).addValue("admin", adminId), VIEW);
        audit.record(adminId, action, "event_report", reportId.toString(), Map.of("status", "OPEN"),
                Map.of("status", status, "note", n));
        return v;
    }
}
