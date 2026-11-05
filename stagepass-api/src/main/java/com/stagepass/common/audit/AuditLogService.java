package com.stagepass.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Append-only audit trail for sensitive actions. Joins the caller's transaction. */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLogService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(UUID actorId, String action, String entityType, String entityId, Object before, Object after) {
        jdbc.update("""
                insert into audit_logs (actor_id, action, entity_type, entity_id, before, after, ip)
                values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                """, actorId, action, entityType, entityId, toJson(before), toJson(after), currentIp());
    }

    private String toJson(Object value) {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
