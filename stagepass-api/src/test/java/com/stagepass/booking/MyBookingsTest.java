package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MyBookingsTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void listsUpcomingAndPastBookings() throws Exception {
        TestUser buyer = users.attendee();
        BookingResponse upcoming = support.confirmed(support.liveEvent(49900, 10), 1, buyer);
        LiveEvent pastEvent = support.liveEvent(49900, 10);
        BookingResponse past = support.confirmed(pastEvent, 2, buyer);
        jdbc.update("update events set starts_at = now() - interval '3 days', ends_at = now() - interval '2 days' where id = ?",
                pastEvent.eventId());
        BookingResponse abandoned = support.book(support.liveEvent(49900, 10), 1, buyer);
        jdbc.update("update bookings set status = 'EXPIRED' where id = ?", abandoned.id());

        mvc.perform(get("/api/v1/me/bookings").header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(upcoming.id().toString()))
                .andExpect(jsonPath("$.items[0].ticketCount").value(1))
                .andExpect(jsonPath("$.items[0].city").value("Bookcity"));
        mvc.perform(get("/api/v1/me/bookings?scope=past").header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(past.id().toString())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(upcoming.id().toString()))))
                .andExpect(jsonPath("$.items[0].ticketCount").value(2));
        mvc.perform(get("/api/v1/me/bookings?scope=later").header("Authorization", buyer.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingDetailIsOwnerOnly() throws Exception {
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 10), 1, buyer);

        mvc.perform(get("/api/v1/me/bookings/{id}", b.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(b.code()))
                .andExpect(jsonPath("$.tickets[0].code").exists());
        mvc.perform(get("/api/v1/me/bookings/{id}", b.id()).header("Authorization", users.attendee().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailShowsRefundEligibility() throws Exception {
        TestUser buyer = users.attendee();
        LiveEvent e = support.liveEvent(49900, 10);
        jdbc.update("update events set refund_policy = 'UNTIL_HOURS_BEFORE', refund_cutoff_hours = 48 where id = ?", e.eventId());
        BookingResponse b = support.confirmed(e, 1, buyer);

        mvc.perform(get("/api/v1/me/bookings/{id}", b.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.selfServe").value(true))
                .andExpect(jsonPath("$.refund.requestable").value(true))
                .andExpect(jsonPath("$.refund.kind").value("ATTENDEE_REQUEST"))
                .andExpect(jsonPath("$.refund.includesFee").value(false))
                .andExpect(jsonPath("$.refund.until").exists());

        LiveEvent strict = support.liveEvent(49900, 10);
        BookingResponse s = support.confirmed(strict, 1, buyer);
        mvc.perform(get("/api/v1/me/bookings/{id}", s.id()).header("Authorization", buyer.bearer()))
                .andExpect(jsonPath("$.refund.selfServe").value(false))
                .andExpect(jsonPath("$.refund.requestable").value(true));
    }
}
