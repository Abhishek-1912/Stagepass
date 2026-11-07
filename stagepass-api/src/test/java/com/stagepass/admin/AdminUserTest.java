package com.stagepass.admin;

import com.stagepass.identity.service.UserProvisioningService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestJwt;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    UserProvisioningService provisioning;

    @Autowired
    JdbcTemplate jdbc;

    private TestUser named(String email, String name) {
        UUID id = UUID.randomUUID();
        provisioning.provision(id, email, name, null);
        return new TestUser(id, email, TestJwt.token(id, email), null);
    }

    private ResultActions suspend(TestUser admin, UUID userId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/users/" + userId + "/suspend").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private int audits(String action, UUID entity) {
        return jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?", Integer.class,
                action, entity.toString());
    }

    @Test
    void searchMatchesEmailAndNameWithLiteralWildcards() throws Exception {
        TestUser admin = users.admin();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        TestUser underscore = named("asha_" + tag + "@stagepass.test", "Asha Rao");
        TestUser plain = named("asharao" + tag + "@stagepass.test", "Asharao " + tag);

        mvc.perform(get("/api/v1/admin/users").param("q", "ASHA_" + tag).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(underscore.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(plain.id().toString()))));
        mvc.perform(get("/api/v1/admin/users").param("q", "rao " + tag).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(plain.id().toString())));
        mvc.perform(get("/api/v1/admin/users").param("q", "%").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/users").param("role", "ADMIN").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(admin.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(plain.id().toString()))));
        suspend(admin, plain.id(), "Spam bookings").andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").param("status", "SUSPENDED").param("q", tag)
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(plain.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(underscore.id().toString()))));
        mvc.perform(get("/api/v1/admin/users").param("role", "BOSS").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("role"));
        mvc.perform(get("/api/v1/admin/users").param("status", "gone").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void suspendedUserIsBlockedOnTheNextRequest() throws Exception {
        TestUser admin = users.admin();
        TestUser attendee = users.attendee();
        mvc.perform(get("/api/v1/me").header("Authorization", attendee.bearer())).andExpect(status().isOk());

        suspend(admin, attendee.id(), "Chargeback fraud")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Chargeback fraud"));

        mvc.perform(get("/api/v1/me").header("Authorization", attendee.bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        Map<String, Object> row = jdbc.queryForMap("select suspended_at, suspension_reason from users where id = ?",
                attendee.id());
        assertThat(row.get("suspended_at")).isNotNull();
        assertThat(audits("USER_SUSPENDED", attendee.id())).isEqualTo(1);

        suspend(admin, attendee.id(), "Again").andExpect(status().isOk());
        assertThat(audits("USER_SUSPENDED", attendee.id())).isEqualTo(1);
        suspend(admin, attendee.id(), "no").andExpect(status().isBadRequest());
    }

    @Test
    void reactivateRestoresAccess() throws Exception {
        TestUser admin = users.admin();
        TestUser attendee = users.attendee();
        suspend(admin, attendee.id(), "Mistake").andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/users/" + attendee.id() + "/reactivate").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.suspensionReason").doesNotExist());
        mvc.perform(get("/api/v1/me").header("Authorization", attendee.bearer())).andExpect(status().isOk());
        Map<String, Object> row = jdbc.queryForMap("select suspended_at, suspension_reason from users where id = ?",
                attendee.id());
        assertThat(row.get("suspended_at")).isNull();
        assertThat(row.get("suspension_reason")).isNull();
        assertThat(audits("USER_REACTIVATED", attendee.id())).isEqualTo(1);
    }

    @Test
    void cannotSuspendSelfOrAnotherAdmin() throws Exception {
        TestUser admin = users.admin();
        TestUser other = users.admin();
        suspend(admin, admin.id(), "Testing").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can't suspend your own account"));
        suspend(admin, other.id(), "Testing").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Remove admin from this account first"));
        suspend(admin, UUID.randomUUID(), "Testing").andExpect(status().isNotFound());
    }

    @Test
    void grantAndRevokeAdmin() throws Exception {
        TestUser admin = users.admin();
        TestUser target = users.attendee();
        mvc.perform(get("/api/v1/admin/users").header("Authorization", target.bearer())).andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles/ADMIN").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItem("ADMIN")));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", target.bearer())).andExpect(status().isOk());
        assertThat(audits("ROLE_GRANTED", target.id())).isEqualTo(1);

        mvc.perform(delete("/api/v1/admin/users/" + target.id() + "/roles/ADMIN").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", not(hasItem("ADMIN"))));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", target.bearer())).andExpect(status().isForbidden());
        assertThat(audits("ROLE_REVOKED", target.id())).isEqualTo(1);

        mvc.perform(delete("/api/v1/admin/users/" + admin.id() + "/roles/ADMIN").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can't remove your own admin access"));
    }

    @Test
    void organizerRoleIsNotGrantableHere() throws Exception {
        TestUser admin = users.admin();
        TestUser target = users.attendee();
        mvc.perform(put("/api/v1/admin/users/" + target.id() + "/roles/ORGANIZER").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("role"));
        mvc.perform(delete("/api/v1/admin/users/" + target.id() + "/roles/ORGANIZER").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminGets403() throws Exception {
        UUID someone = users.attendee().id();
        for (TestUser caller : new TestUser[] {users.attendee(), users.approvedOrganizer()}) {
            mvc.perform(get("/api/v1/admin/users").header("Authorization", caller.bearer())).andExpect(status().isForbidden());
            suspend(caller, someone, "Testing").andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/users/" + someone + "/reactivate").header("Authorization", caller.bearer()))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/admin/users/" + someone + "/roles/ADMIN").header("Authorization", caller.bearer()))
                    .andExpect(status().isForbidden());
        }
    }
}
