package com.stagepass.admin;

import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogQueriesTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private void audit(UUID actor, String action, String type, String entity, String at) {
        jdbc.update("""
                insert into audit_logs (actor_id, action, entity_type, entity_id, before, after, at)
                values (?, ?, ?, ?, '{"status":"OLD"}'::jsonb, '{"status":"NEW","n":2}'::jsonb, ?::timestamptz)
                """, actor, action, type, entity, at);
    }

    @Test
    void filtersAndOrder() throws Exception {
        TestUser admin = users.admin();
        TestUser actor = users.attendee();
        String action = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String entity = UUID.randomUUID().toString();
        audit(actor.id(), action, "event", entity, "2026-12-01 23:30:00+05:30");
        audit(actor.id(), action, "event", entity, "2026-12-02 09:00:00+05:30");
        audit(actor.id(), action, "user", "other", "2026-12-02 10:00:00+05:30");
        audit(admin.id(), action, "event", entity, "2026-12-02 11:00:00+05:30");

        mvc.perform(get("/api/v1/admin/audit").param("action", action).param("actor", actor.email().toUpperCase())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].entityType").value("user"))
                .andExpect(jsonPath("$.items[0].actorEmail").value(actor.email()))
                .andExpect(jsonPath("$.items[0].before.status").value("OLD"))
                .andExpect(jsonPath("$.items[0].after.n").value(2));
        mvc.perform(get("/api/v1/admin/audit").param("action", action).param("entityType", "event").param("entityId", entity)
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/v1/admin/audit").param("action", action).param("from", "2026-12-01").param("to", "2026-12-01")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/admin/audit").param("action", action).param("actor", "nobody@stagepass.test")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/audit").param("from", "2026-12-05").param("to", "2026-12-01")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/audit").header("Authorization", actor.bearer())).andExpect(status().isForbidden());
    }
}
