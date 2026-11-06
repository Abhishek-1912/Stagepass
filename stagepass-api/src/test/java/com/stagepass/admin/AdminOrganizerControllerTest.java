package com.stagepass.admin;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.identity.OrganizerApplicationControllerTest;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOrganizerControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private String applyAs(TestUser user, String contactEmail) throws Exception {
        String body = OrganizerApplicationControllerTest.VALID.replace("hello@acme.test", contactEmail);
        return JsonPath.read(mvc.perform(post("/api/v1/organizer-applications").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void adminListsPendingApplicationsOldestFirst() throws Exception {
        TestUser admin = users.admin();
        String first = applyAs(users.attendee(), "first@acme.test");
        String second = applyAs(users.attendee(), "second@acme.test");

        String json = mvc.perform(get("/api/v1/admin/organizer-applications?status=PENDING&size=100")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(first)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$.items[*].id");
        assertThat(ids.indexOf(first)).isLessThan(ids.indexOf(second));
    }

    @Test
    void approvingGrantsOrganizerRoleImmediately() throws Exception {
        TestUser applicant = users.attendee();
        String id = applyAs(applicant, "approved@acme.test");

        mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/approve")
                        .header("Authorization", users.admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/v1/me").header("Authorization", applicant.bearer()))
                .andExpect(jsonPath("$.roles", hasItem("ORGANIZER")));
        assertThat(jdbc.queryForObject(
                "select count(*) from email_outbox where to_address = 'approved@acme.test' and template = 'ORGANIZER_APPROVED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'ORGANIZER_APPROVED' and entity_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void rejectingRequiresReasonAndEmailsIt() throws Exception {
        TestUser admin = users.admin();
        String id = applyAs(users.attendee(), "rejected@acme.test");

        mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/reject").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/reject").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"PAN mismatch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("PAN mismatch"));

        assertThat(jdbc.queryForObject(
                "select payload->>'reason' from email_outbox where to_address = 'rejected@acme.test' and template = 'ORGANIZER_REJECTED'",
                String.class)).isEqualTo("PAN mismatch");
    }

    @Test
    void approvingTwiceIs409InvalidState() throws Exception {
        TestUser admin = users.admin();
        String id = applyAs(users.attendee(), "twice@acme.test");
        mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/approve").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/approve").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void unknownApplicationIs404() throws Exception {
        mvc.perform(post("/api/v1/admin/organizer-applications/" + java.util.UUID.randomUUID() + "/approve")
                        .header("Authorization", users.admin().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminGets403() throws Exception {
        mvc.perform(post("/api/v1/admin/organizer-applications/" + java.util.UUID.randomUUID() + "/approve")
                        .header("Authorization", users.attendee().bearer()))
                .andExpect(status().isForbidden());
    }
}
