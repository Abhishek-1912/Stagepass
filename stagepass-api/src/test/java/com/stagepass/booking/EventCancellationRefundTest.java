package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventCancellationRefundTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cancellingEventRefundsEveryBookingInFull() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse a = support.confirmed(e, 2, users.attendee());
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        BookingResponse pending = support.book(e, 1, users.attendee());

        mvc.perform(post("/api/v1/organizer/events/{id}/cancel", e.eventId()).header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Venue flooded\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> refunds = jdbc.queryForList("""
                select r.booking_id, r.kind, r.status, r.amount_paise from refunds r join bookings b on b.id = r.booking_id
                where b.event_id = ? order by r.amount_paise desc
                """, e.eventId());
        assertThat(refunds).hasSize(2);
        assertThat(refunds.get(0)).containsEntry("booking_id", a.id()).containsEntry("kind", "CANCELLATION")
                .containsEntry("status", "PROCESSING").containsEntry("amount_paise", a.price().totalPaise());
        assertThat(refunds.get(1)).containsEntry("booking_id", b.id()).containsEntry("amount_paise", b.price().totalPaise());

        assertThat(jdbc.queryForObject("select count(*) from tickets where event_id = ? and status <> 'REFUNDED'",
                Integer.class, e.eventId())).isZero();
        assertThat(jdbc.queryForList("select status from bookings where id in (?, ?)", String.class, a.id(), b.id()))
                .containsOnly("REFUNDED");
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, pending.id()))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForMap("select sold, held from ticket_inventory where ticket_type_id = ?", e.ticketTypeId()))
                .containsEntry("sold", 0).containsEntry("held", 0);
    }
}
