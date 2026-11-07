package com.stagepass.engagement;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavedEventTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions save(TestUser u, UUID eventId) throws Exception {
        return mvc.perform(put("/api/v1/me/saved/" + eventId).header("Authorization", u.bearer()));
    }

    private int rows(TestUser u) {
        return jdbc.queryForObject("select count(*) from saved_events where user_id = ?", Integer.class, u.id());
    }

    @Test
    void saveIsIdempotentAndListed() throws Exception {
        TestUser me = users.attendee();
        LiveEvent first = support.liveEvent(49900, 20);
        LiveEvent second = support.liveEvent(49900, 20);
        save(me, first.eventId()).andExpect(status().isNoContent());
        save(me, first.eventId()).andExpect(status().isNoContent());
        jdbc.update("update saved_events set created_at = now() - interval '1 hour' where user_id = ?", me.id());
        save(me, second.eventId()).andExpect(status().isNoContent());

        assertThat(rows(me)).isEqualTo(2);
        mvc.perform(get("/api/v1/me/saved").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].event.id").value(second.eventId().toString()))
                .andExpect(jsonPath("$.items[1].event.id").value(first.eventId().toString()))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/me/saved/ids").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$", hasItem(first.eventId().toString())))
                .andExpect(jsonPath("$", hasItem(second.eventId().toString())));
    }

    @Test
    void onlyVisibleEventsCanBeSaved() throws Exception {
        TestUser me = users.attendee();
        TestUser org = users.approvedOrganizer();
        UUID draft = catalog.draftEvent(org);
        UUID rejected = catalog.draftEvent(org);
        jdbc.update("update events set status = 'REJECTED' where id = ?", rejected);
        LiveEvent ended = support.liveEvent(49900, 20);
        jdbc.update("update events set starts_at = now() - interval '5 hours', ends_at = now() - interval '1 hour' where id = ?",
                ended.eventId());
        LiveEvent suspended = support.liveEvent(49900, 20);
        jdbc.update("update organizers set status = 'SUSPENDED' where id = ?", suspended.organizer().organizerId());

        for (UUID id : new UUID[] {draft, rejected, ended.eventId(), suspended.eventId(), UUID.randomUUID()}) {
            save(me, id).andExpect(status().isNotFound());
        }
        assertThat(rows(me)).isZero();
    }

    @Test
    void unsaveIsIdempotent() throws Exception {
        TestUser me = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        save(me, e.eventId()).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/me/saved/" + e.eventId()).header("Authorization", me.bearer())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/me/saved/" + e.eventId()).header("Authorization", me.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/saved/ids").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$", not(hasItem(e.eventId().toString()))));
    }

    @Test
    void endedSavedEventStaysInTheList() throws Exception {
        TestUser me = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        save(me, e.eventId()).andExpect(status().isNoContent());
        jdbc.update("update events set status = 'COMPLETED', starts_at = now() - interval '5 hours',"
                + " ends_at = now() - interval '1 hour' where id = ?", e.eventId());

        mvc.perform(get("/api/v1/me/saved").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.items[0].event.id").value(e.eventId().toString()))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"));
    }

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(get("/api/v1/me/saved")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/me/saved/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedOrganizersEventsLeaveTheSavedList() throws Exception {
        TestUser me = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        save(me, e.eventId()).andExpect(status().isNoContent());
        jdbc.update("update organizers set status = 'SUSPENDED' where id = ?", e.organizer().organizerId());
        mvc.perform(get("/api/v1/me/saved").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
