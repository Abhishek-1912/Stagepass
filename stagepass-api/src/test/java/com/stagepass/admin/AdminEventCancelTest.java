package com.stagepass.admin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminEventCancelTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions cancel(TestUser admin, UUID eventId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/events/" + eventId + "/cancel").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    @Test
    void adminCancelsPublishedEventAndRefundsEveryone() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse a = support.confirmed(e, 2, users.attendee());
        BookingResponse b = support.confirmed(e, 1, users.attendee());

        cancel(admin, e.eventId(), "Organizer unreachable").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.queryForList("select status from bookings where id in (?, ?)", String.class, a.id(), b.id()))
                .containsOnly("REFUNDED");
        assertThat(jdbc.queryForObject("select count(*) from refunds r join bookings b on b.id = r.booking_id"
                + " where b.event_id = ? and r.kind = 'CANCELLATION'", Integer.class, e.eventId())).isEqualTo(2);
        String after = jdbc.queryForObject("select after::text from audit_logs where action = 'EVENT_CANCELLED' and entity_id = ?",
                String.class, e.eventId().toString());
        assertThat(after).contains("\"byAdmin\": true").contains("Organizer unreachable");
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where template = 'EVENT_CANCELLED'"
                + " and payload->>'bookingCode' in (?, ?)", Integer.class, a.code(), b.code())).isEqualTo(2);
    }

    @Test
    void adminCancelNeedsReasonAndALiveEvent() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        cancel(admin, e.eventId(), "x").andExpect(status().isBadRequest());
        jdbc.update("update events set status = 'COMPLETED' where id = ?", e.eventId());
        cancel(admin, e.eventId(), "Too late now").andExpect(status().isConflict());
        cancel(admin, UUID.randomUUID(), "Unknown one").andExpect(status().isNotFound());
        cancel(e.organizer(), e.eventId(), "Not an admin").andExpect(status().isForbidden());
    }

    @Test
    void adminEventListSearch() throws Exception {
        TestUser admin = users.admin();
        TestUser org = users.approvedOrganizer();
        String name = "Searchable " + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("update organizers set org_name = ? where id = ?", name, org.organizerId());
        LiveEvent mine = support.liveEvent(org, 49900, 20);
        LiveEvent other = support.liveEvent(49900, 20);

        mvc.perform(get("/api/v1/admin/events").param("status", "PUBLISHED").param("q", name.toLowerCase())
                        .param("size", "100").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(mine.eventId().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(other.eventId().toString()))));
        mvc.perform(get("/api/v1/admin/events").param("status", "PUBLISHED").param("q", "%_")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
}
