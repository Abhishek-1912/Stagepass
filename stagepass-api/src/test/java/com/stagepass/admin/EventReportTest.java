package com.stagepass.admin;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventReportTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions report(TestUser user, UUID eventId, String json) throws Exception {
        return mvc.perform(post("/api/v1/events/" + eventId + "/reports").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions decide(TestUser admin, String reportId, String action, String note) throws Exception {
        return mvc.perform(post("/api/v1/admin/reports/" + reportId + "/" + action).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + note + "\"}"));
    }

    private static String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void attendeeReportsOnceWhileOpen() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        TestUser user = users.attendee();

        String first = id(report(user, e.eventId(), "{\"reason\":\"SCAM\",\"details\":\"Same event listed twice\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN")));
        String again = id(report(user, e.eventId(), "{\"reason\":\"misleading\"}").andExpect(status().isOk()));
        assertThat(again).isEqualTo(first);

        report(users.attendee(), e.eventId(), "{\"reason\":\"OTHER\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("details"));
        report(users.attendee(), e.eventId(), "{\"reason\":\"BORING\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        UUID draft = catalog.draftEvent(e.organizer());
        report(user, draft, "{\"reason\":\"SCAM\"}").andExpect(status().isNotFound());
        report(user, UUID.randomUUID(), "{\"reason\":\"SCAM\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/events/" + e.eventId() + "/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SCAM\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminResolvesAndDismisses() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        String a = id(report(users.attendee(), e.eventId(), "{\"reason\":\"SCAM\"}"));
        String b = id(report(users.attendee(), e.eventId(), "{\"reason\":\"OFFENSIVE\",\"details\":\"Cover photo\"}"));

        mvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + a + "')].openReportsForEvent").value(org.hamcrest.Matchers.hasItem(2)))
                .andExpect(jsonPath("$.items[?(@.id == '" + b + "')].details").value(org.hamcrest.Matchers.hasItem("Cover photo")));

        decide(admin, a, "resolve", "Asked organizer to fix the listing").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(jdbc.queryForObject("select resolved_by from event_reports where id = ?::uuid", UUID.class, a))
                .isEqualTo(admin.id());
        decide(admin, a, "dismiss", "Changed my mind").andExpect(status().isConflict());
        decide(admin, b, "dismiss", "x").andExpect(status().isBadRequest());
        decide(admin, b, "dismiss", "Not offensive").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISMISSED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REPORT_RESOLVED' and entity_id = ?",
                Integer.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REPORT_DISMISSED' and entity_id = ?",
                Integer.class, b)).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/reports").param("status", "LOST").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reports").header("Authorization", e.organizer().bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void afterResolutionUserCanReportAgain() throws Exception {
        TestUser admin = users.admin();
        TestUser user = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        String first = id(report(user, e.eventId(), "{\"reason\":\"SCAM\"}"));
        decide(admin, first, "resolve", "Handled it").andExpect(status().isOk());

        String second = id(report(user, e.eventId(), "{\"reason\":\"SCAM\"}").andExpect(status().isCreated()));
        assertThat(second).isNotEqualTo(first);
    }
}
