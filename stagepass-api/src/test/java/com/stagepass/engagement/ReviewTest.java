package com.stagepass.engagement;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserProvisioningService provisioning;

    private TestUser named(String name) {
        UUID id = UUID.randomUUID();
        String email = "rev-" + id + "@stagepass.test";
        provisioning.provision(id, email, name, null);
        return new TestUser(id, email, TestJwt.token(id, email), null);
    }

    private void ended(UUID eventId) {
        jdbc.update("update events set status = 'COMPLETED', starts_at = now() - interval '5 hours',"
                + " ends_at = now() - interval '2 hours' where id = ?", eventId);
    }

    private String slug(UUID eventId) {
        return jdbc.queryForObject("select slug from events where id = ?", String.class, eventId);
    }

    private ResultActions review(TestUser u, UUID eventId, String json) throws Exception {
        return mvc.perform(put("/api/v1/events/" + eventId + "/review").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void attendeeReviewsAfterTheEvent() throws Exception {
        TestUser asha = named("Asha Rao");
        LiveEvent e = support.liveEvent(49900, 20);
        support.confirmed(e, 1, asha);
        ended(e.eventId());

        String id = JsonPath.read(review(asha, e.eventId(), "{\"rating\":5,\"comment\":\"Loved it\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        review(asha, e.eventId(), "{\"rating\":4,\"comment\":\"Great, long queue\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.rating").value(4));

        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId()) + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].authorName").value("Asha"))
                .andExpect(jsonPath("$.items[0].comment").value("Great, long queue"));
        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId())))
                .andExpect(jsonPath("$.rating.average").value(4.0))
                .andExpect(jsonPath("$.rating.count").value(1));
        mvc.perform(get("/api/v1/events/" + e.eventId() + "/review/mine").header("Authorization", asha.bearer()))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.review.rating").value(4));
    }

    @Test
    void notEligibleIs403() throws Exception {
        LiveEvent upcoming = support.liveEvent(49900, 20);
        TestUser early = users.attendee();
        support.confirmed(upcoming, 1, early);
        review(early, upcoming.eventId(), "{\"rating\":5}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You can review events you attended, after they end"));

        LiveEvent past = support.liveEvent(49900, 20);
        TestUser refunded = users.attendee();
        BookingResponse b = support.confirmed(past, 1, refunded);
        jdbc.update("update tickets set status = 'REFUNDED' where booking_id = ?", b.id());
        jdbc.update("update bookings set status = 'REFUNDED' where id = ?", b.id());
        ended(past.eventId());
        review(refunded, past.eventId(), "{\"rating\":1}").andExpect(status().isForbidden());
        review(users.attendee(), past.eventId(), "{\"rating\":1}").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/events/" + past.eventId() + "/review/mine").header("Authorization", refunded.bearer()))
                .andExpect(jsonPath("$.eligible").value(false));

        LiveEvent cancelled = support.liveEvent(49900, 20);
        TestUser holder = users.attendee();
        support.confirmed(cancelled, 1, holder);
        jdbc.update("update events set status = 'CANCELLED', starts_at = now() - interval '5 hours',"
                + " ends_at = now() - interval '2 hours' where id = ?", cancelled.eventId());
        review(holder, cancelled.eventId(), "{\"rating\":1}").andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from reviews where event_id in (?, ?, ?)", Integer.class,
                upcoming.eventId(), past.eventId(), cancelled.eventId())).isZero();
    }

    @Test
    void checkedInTicketCounts() throws Exception {
        TestUser me = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, me);
        jdbc.update("update tickets set status = 'CHECKED_IN' where booking_id = ?", b.id());
        ended(e.eventId());
        review(me, e.eventId(), "{\"rating\":3}").andExpect(status().isCreated());
    }

    @Test
    void validation() throws Exception {
        TestUser me = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        support.confirmed(e, 1, me);
        ended(e.eventId());
        for (String bad : new String[] {"{\"rating\":0}", "{\"rating\":6}", "{}"}) {
            review(me, e.eventId(), bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("rating"));
        }
        review(me, e.eventId(), "{\"rating\":4,\"comment\":\"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("comment"));
    }

    @Test
    void deleteOwnAndAdminRemove() throws Exception {
        TestUser me = users.attendee();
        TestUser other = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        support.confirmed(e, 1, me);
        support.confirmed(e, 1, other);
        ended(e.eventId());
        review(me, e.eventId(), "{\"rating\":2}").andExpect(status().isCreated());
        String otherId = JsonPath.read(review(other, e.eventId(), "{\"rating\":1,\"comment\":\"spam spam\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(delete("/api/v1/events/" + e.eventId() + "/review").header("Authorization", me.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from reviews where user_id = ?", Integer.class, me.id())).isZero();

        mvc.perform(delete("/api/v1/admin/reviews/" + otherId).header("Authorization", me.bearer()))
                .andExpect(status().isForbidden());
        TestUser admin = users.admin();
        mvc.perform(delete("/api/v1/admin/reviews/" + otherId).header("Authorization", admin.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from reviews where id = ?::uuid", Integer.class, otherId)).isZero();
        assertThat(jdbc.queryForObject("select before::text from audit_logs where action = 'REVIEW_REMOVED' and entity_id = ?",
                String.class, otherId)).contains("spam spam");
        mvc.perform(delete("/api/v1/admin/reviews/" + otherId).header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void organizerProfileAveragesAllEvents() throws Exception {
        TestUser org = users.approvedOrganizer();
        LiveEvent first = support.liveEvent(org, 49900, 20);
        LiveEvent second = support.liveEvent(org, 49900, 20);
        TestUser a = users.attendee();
        TestUser b = users.attendee();
        support.confirmed(first, 1, a);
        support.confirmed(second, 1, b);
        ended(first.eventId());
        ended(second.eventId());
        review(a, first.eventId(), "{\"rating\":5}").andExpect(status().isCreated());
        review(b, second.eventId(), "{\"rating\":3}").andExpect(status().isCreated());

        String orgSlug = jdbc.queryForObject("select slug from organizers where id = ?", String.class, org.organizerId());
        mvc.perform(get("/api/v1/public/organizers/" + orgSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.average").value(4.0))
                .andExpect(jsonPath("$.rating.count").value(2));
    }

    @Test
    void authorNameNeverLeaksEmail() throws Exception {
        TestUser nameless = named(null);
        LiveEvent e = support.liveEvent(49900, 20);
        support.confirmed(e, 1, nameless);
        ended(e.eventId());
        review(nameless, e.eventId(), "{\"rating\":5}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Stagepass guest"));
        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId()) + "/reviews"))
                .andExpect(jsonPath("$.items[0].authorName").value("Stagepass guest"));
        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId())))
                .andExpect(jsonPath("$.rating.count").value(1));
    }
}
