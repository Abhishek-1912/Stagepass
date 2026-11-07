package com.stagepass.admin.service;

import com.stagepass.admin.api.AuditRow;
import com.stagepass.common.api.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/** Read-only audit log search for admins (spec 9, 11), newest first. */
@Service
public class AuditLogQueries {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NamedParameterJdbcTemplate sql;

    public AuditLogQueries(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditRow> search(String actorEmail, String action, String entityType, String entityId,
            LocalDate from, LocalDate to, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder("from audit_logs a left join users u on u.id = a.actor_id where true");
        if (AdminQueries.present(actorEmail)) {
            p.addValue("actor", actorEmail.strip().toLowerCase(Locale.ROOT));
            body.append(" and lower(u.email) = :actor");
        }
        if (AdminQueries.present(action)) {
            p.addValue("action", action.strip().toUpperCase(Locale.ROOT));
            body.append(" and a.action = :action");
        }
        if (AdminQueries.present(entityType)) {
            p.addValue("entityType", entityType.strip().toLowerCase(Locale.ROOT));
            body.append(" and a.entity_type = :entityType");
        }
        if (AdminQueries.present(entityId)) {
            p.addValue("entityId", entityId.strip());
            body.append(" and a.entity_id = :entityId");
        }
        body.append(AdminQueries.dateRange("a.at", from, to, p));
        return AdminQueries.page(sql, """
                select a.id, a.at, u.email, a.action, a.entity_type, a.entity_id, a.before::text as before_json,
                       a.after::text as after_json
                """, body.toString(), "a.at desc, a.id", p, page, size, (rs, n) -> new AuditRow(
                rs.getObject("id", UUID.class), rs.getObject("at", OffsetDateTime.class), rs.getString("email"),
                rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"),
                json(rs.getString("before_json")), json(rs.getString("after_json"))));
    }

    private static JsonNode json(String text) {
        return text == null ? null : JSON.readTree(text);
    }
}
