package com.stagepass.checkin.service;

import com.stagepass.checkin.api.StaffResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Door staff of an organizer: invite by email (one event or all), list, remove. Staff can only scan. */
@Service
public class StaffService {

    /** The invite and whether this call created it. */
    public record Invited(StaffResponse staff, boolean created) {
    }

    private static final String SELECT = """
            select s.id, s.email, u.full_name, s.event_id, e.title, s.status, s.created_at, s.accepted_at
            from event_staff s
            left join users u on u.id = s.user_id
            left join events e on e.id = s.event_id
            """;
    private static final RowMapper<StaffResponse> ROW = (rs, n) -> new StaffResponse(rs.getObject("id", UUID.class),
            rs.getString("email"), rs.getString("full_name"), rs.getObject("event_id", UUID.class), rs.getString("title"),
            rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("accepted_at", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;
    private final EmailOutbox outbox;

    public StaffService(NamedParameterJdbcTemplate sql, AuditLogService audit, EmailOutbox outbox) {
        this.sql = sql;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list(UUID organizerId) {
        return sql.query(SELECT + " where s.organizer_id = :org order by (s.status = 'ACTIVE') desc, s.created_at desc",
                new MapSqlParameterSource("org", organizerId), ROW);
    }

    @Transactional
    public Invited invite(UUID organizerId, UUID actorId, String rawEmail, UUID eventId) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        String eventTitle = null;
        if (eventId != null) {
            List<String> titles = sql.queryForList("select title from events where id = :event and organizer_id = :org",
                    new MapSqlParameterSource("event", eventId).addValue("org", organizerId), String.class);
            eventTitle = titles.stream().findFirst().orElseThrow(() -> ApiException.notFound("Event"));
        }
        MapSqlParameterSource p = new MapSqlParameterSource("org", organizerId).addValue("email", email)
                .addValue("event", eventId).addValue("actor", actorId);
        List<StaffResponse> existing = sql.query(SELECT + """
                 where s.organizer_id = :org and lower(s.email) = :email and s.status = 'ACTIVE'
                   and s.event_id is not distinct from cast(:event as uuid)
                """, p, ROW);
        if (!existing.isEmpty()) {
            return new Invited(existing.get(0), false);
        }
        UUID id = sql.queryForObject("""
                insert into event_staff (organizer_id, event_id, email, invited_by) values (:org, :event, :email, :actor)
                returning id
                """, p, UUID.class);
        String orgName = sql.queryForObject("select org_name from organizers where id = :org", p, String.class);
        audit.record(actorId, "STAFF_INVITED", "event_staff", id.toString(), null,
                eventId == null ? Map.of("email", email, "scope", "ALL_EVENTS") : Map.of("email", email, "eventId", eventId.toString()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organizerName", orgName);
        if (eventTitle != null) {
            data.put("eventTitle", eventTitle);
        }
        outbox.enqueue(email, EmailTemplate.STAFF_INVITED, data, "staff-invited:" + id);
        return new Invited(sql.query(SELECT + " where s.id = :id", new MapSqlParameterSource("id", id), ROW).get(0), true);
    }

    @Transactional
    public void remove(UUID organizerId, UUID actorId, UUID staffId) {
        int n = sql.update("""
                update event_staff set status = 'REMOVED', removed_at = now()
                where id = :id and organizer_id = :org and status = 'ACTIVE'
                """, new MapSqlParameterSource("id", staffId).addValue("org", organizerId));
        if (n == 0) {
            Integer exists = sql.queryForObject("select count(*) from event_staff where id = :id and organizer_id = :org",
                    new MapSqlParameterSource("id", staffId).addValue("org", organizerId), Integer.class);
            if (exists == null || exists == 0) {
                throw ApiException.notFound("Staff member");
            }
            return;
        }
        audit.record(actorId, "STAFF_REMOVED", "event_staff", staffId.toString(), null, Map.of("status", "REMOVED"));
    }
}
