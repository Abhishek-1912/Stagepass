package com.stagepass.booking;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateBookingTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions create(TestUser buyer, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/bookings").header("Authorization", buyer.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int held(UUID type) {
        return jdbc.queryForObject("select held from ticket_inventory where ticket_type_id = ?", Integer.class, type);
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    @Test
    void createsPendingBookingHoldingInventory() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        String json = create(users.attendee(), key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.code").value(startsWith("SP-")))
                .andExpect(jsonPath("$.price.totalPaise").value(105104))
                .andExpect(jsonPath("$.checkout.razorpayKeyId").value("rzp_test_key"))
                .andExpect(jsonPath("$.checkout.razorpayOrderId").value(startsWith("order_")))
                .andExpect(jsonPath("$.checkout.amountPaise").value(105104))
                .andReturn().getResponse().getContentAsString();

        assertThat(held(e.ticketTypeId())).isEqualTo(2);
        OffsetDateTime expires = OffsetDateTime.parse(JsonPath.read(json, "$.holdExpiresAt"));
        assertThat(expires).isBetween(OffsetDateTime.now().plusMinutes(9), OffsetDateTime.now().plusMinutes(11));
    }

    @Test
    void sameIdempotencyKeyReturnsSameBooking() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        TestUser buyer = users.attendee();
        String k = key();
        String body = BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2);
        String id = JsonPath.read(create(buyer, k, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        create(buyer, k, body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(held(e.ticketTypeId())).isEqualTo(2);
    }

    @Test
    void reusedKeyWithDifferentItemsIs409() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        TestUser buyer = users.attendee();
        String k = key();
        create(buyer, k, BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2)).andExpect(status().isCreated());

        create(buyer, k, BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 3)).andExpect(status().isConflict());
        create(users.attendee(), k, BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2)).andExpect(status().isConflict());
    }

    @Test
    void newBookingExpiresPreviousPendingOne() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        TestUser buyer = users.attendee();
        String first = JsonPath.read(create(buyer, key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 3))
                .andReturn().getResponse().getContentAsString(), "$.id");

        create(buyer, key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 1)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select status from bookings where id = ?::uuid", String.class, first)).isEqualTo("EXPIRED");
        assertThat(held(e.ticketTypeId())).isEqualTo(1);
    }

    @Test
    void perUserLimitCountsConfirmedAndPending() throws Exception {
        LiveEvent e = support.liveEvent(100, 50);
        jdbc.update("update events set max_tickets_per_user = 4 where id = ?", e.eventId());
        TestUser buyer = users.attendee();
        String firstId = JsonPath.read(create(buyer, key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 3))
                .andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("update bookings set status = 'CONFIRMED' where id = ?::uuid", firstId);

        create(buyer, key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        create(buyer, key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 1)).andExpect(status().isCreated());
    }

    @Test
    void freeEventConfirmsImmediately() throws Exception {
        LiveEvent e = support.liveEvent(0, 20);
        long ordersBefore = SharedRazorpay.server().count(r -> r.path().equals("/v1/orders"));

        create(users.attendee(), key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.price.totalPaise").value(0))
                .andExpect(jsonPath("$.checkout").doesNotExist())
                .andExpect(jsonPath("$.tickets", hasSize(2)))
                .andExpect(jsonPath("$.tickets[0].code").value(startsWith("SP-")));

        assertThat(SharedRazorpay.server().count(r -> r.path().equals("/v1/orders"))).isEqualTo(ordersBefore);
        assertThat(jdbc.queryForObject("select sold from ticket_inventory where ticket_type_id = ?", Integer.class,
                e.ticketTypeId())).isEqualTo(2);
        assertThat(held(e.ticketTypeId())).isZero();
    }

    @Test
    void razorpayOutageReleasesHolds() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        SharedRazorpay.server().failPath("/v1/orders", 500);
        try {
            create(users.attendee(), key(), BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 2))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            SharedRazorpay.server().clearFailures();
        }
        assertThat(held(e.ticketTypeId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookings where event_id = ? and status = 'EXPIRED'",
                Integer.class, e.eventId())).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        LiveEvent e = support.liveEvent(100, 5);
        mvc.perform(post("/api/v1/bookings").header("Authorization", users.attendee().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        LiveEvent e = support.liveEvent(100, 5);
        mvc.perform(post("/api/v1/bookings").header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 1)))
                .andExpect(status().isUnauthorized());
    }
}
