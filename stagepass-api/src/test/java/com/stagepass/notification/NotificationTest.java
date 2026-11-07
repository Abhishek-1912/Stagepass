package com.stagepass.notification;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.RefundService;
import com.stagepass.notification.service.NotificationService;
import com.stagepass.payment.service.PaymentService;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationService notifications;

    @Autowired
    RefundService refunds;

    @Autowired
    PaymentService payments;

    private List<Map<String, Object>> rows(UUID userId, String type) {
        return jdbc.queryForList("select title, body, link from notifications where user_id = ? and type = ?", userId, type);
    }

    @Test
    void confirmedBookingNotifiesTheBuyerOnce() {
        TestUser buyer = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse pending = support.book(e, 1, buyer);
        String pay = BookingTestSupport.capturedPayment(pending);
        payments.settle(pay);
        payments.settle(pay);

        List<Map<String, Object>> found = rows(buyer.id(), "BOOKING_CONFIRMED");
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).containsEntry("link", "/account/bookings/" + pending.id());
        assertThat((String) found.get(0).get("title")).startsWith("Booking confirmed");
    }

    @Test
    void refundAndCancellationNotify() throws Exception {
        TestUser buyer = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, buyer);
        mvc.perform(post("/api/v1/admin/bookings/" + b.id() + "/refund").header("Authorization", users.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ticketIds\":[],\"includeFee\":true,\"reason\":\"Test refund\"}"))
                .andExpect(status().isOk());
        Map<String, Object> r = jdbc.queryForMap("select id, razorpay_refund_id from refunds where booking_id = ?", b.id());
        refunds.onProcessed((String) r.get("razorpay_refund_id"), r.get("id").toString());
        assertThat(rows(buyer.id(), "REFUND_PROCESSED")).hasSize(1);

        TestUser other = users.attendee();
        support.confirmed(e, 1, other);
        mvc.perform(post("/api/v1/organizer/events/{id}/cancel", e.eventId()).header("Authorization", e.organizer().bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Venue flooded\"}"))
                .andExpect(status().isOk());
        assertThat(rows(other.id(), "EVENT_CANCELLED")).hasSize(1);
    }

    @Test
    void organizerAndEventDecisionsNotifyTheOrganizer() throws Exception {
        TestUser admin = users.admin();
        TestUser applicant = users.pendingOrganizer();
        mvc.perform(post("/api/v1/admin/organizer-applications/" + applicant.organizerId() + "/approve")
                .header("Authorization", admin.bearer())).andExpect(status().isOk());
        assertThat(rows(applicant.id(), "ORGANIZER_APPROVED")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("link", "/organizer"));

        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        jdbc.update("update events set status = 'PENDING_REVIEW' where id = ?", event);
        mvc.perform(post("/api/v1/admin/events/" + event + "/reject").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cover photo is blurry\"}"))
                .andExpect(status().isOk());
        assertThat(rows(org.id(), "EVENT_REJECTED")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("link", "/organizer/events/" + event));
    }

    @Test
    void aSecondRejectionAfterReapplyingAlsoNotifies() throws Exception {
        TestUser admin = users.admin();
        TestUser applicant = users.pendingOrganizer();
        for (String reason : new String[] {"PAN does not match", "Website is not reachable"}) {
            jdbc.update("update organizers set status = 'PENDING' where id = ?", applicant.organizerId());
            mvc.perform(post("/api/v1/admin/organizer-applications/" + applicant.organizerId() + "/reject")
                    .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"" + reason + "\"}")).andExpect(status().isOk());
        }
        assertThat(rows(applicant.id(), "ORGANIZER_REJECTED")).extracting(r -> (String) r.get("body"))
                .containsExactlyInAnyOrder("Reason: PAN does not match", "Reason: Website is not reachable");
    }

    @Test
    void listUnreadCountAndMarkRead() throws Exception {
        TestUser me = users.attendee();
        notifications.notify(me.id(), "TEST", "First", "one", "/a", "t1-" + UUID.randomUUID());
        jdbc.update("update notifications set created_at = now() - interval '1 minute' where user_id = ?", me.id());
        notifications.notify(me.id(), "TEST", "Second", "two", "/b", "t2-" + UUID.randomUUID());

        String json = mvc.perform(get("/api/v1/me/notifications").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Second"))
                .andExpect(jsonPath("$.items[1].title").value("First"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.count").value(2));

        String first = JsonPath.read(json, "$.items[1].id");
        mvc.perform(post("/api/v1/me/notifications/" + first + "/read").header("Authorization", me.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/api/v1/me/notifications/read-all").header("Authorization", me.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.items[1].read").value(true));
    }

    @Test
    void cannotTouchAnotherUsersNotification() throws Exception {
        TestUser owner = users.attendee();
        TestUser intruder = users.attendee();
        String key = "own-" + UUID.randomUUID();
        notifications.notify(owner.id(), "TEST", "Mine", null, "/x", key);
        UUID id = jdbc.queryForObject("select id from notifications where dedupe_key = ?", UUID.class, key);

        mvc.perform(post("/api/v1/me/notifications/" + id + "/read").header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select read_at from notifications where id = ?", Object.class, id)).isNull();
        mvc.perform(post("/api/v1/me/notifications/" + UUID.randomUUID() + "/read").header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void longTextIsTrimmedAndDuplicatesIgnored() {
        TestUser me = users.attendee();
        String key = "long-" + UUID.randomUUID();
        notifications.notify(me.id(), "TEST", "x".repeat(200), "y".repeat(400), "/x", key);
        notifications.notify(me.id(), "TEST", "again", null, "/x", key);
        Map<String, Object> row = jdbc.queryForMap("select title, body from notifications where dedupe_key = ?", key);
        assertThat((String) row.get("title")).hasSize(120).endsWith("…");
        assertThat((String) row.get("body")).hasSize(300).endsWith("…");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Integer.class, me.id())).isEqualTo(1);
    }

    @Test
    void signedOutGets401() throws Exception {
        mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
    }
}
