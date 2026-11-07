package com.stagepass.booking.service;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The per-booking claim re-checks the booking, for refunds that land after the candidate list was read. */
class ReminderClaimTest extends PostgresIntegrationTest {

    @Autowired
    ReminderJob job;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void refundedBetweenSelectAndClaimIsSkipped() {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        jdbc.update("update events set starts_at = now() + interval '20 hours', ends_at = now() + interval '23 hours' where id = ?",
                e.eventId());
        jdbc.update("update tickets set status = 'REFUNDED' where booking_id = ?", b.id());
        jdbc.update("update bookings set status = 'REFUNDED' where id = ?", b.id());

        assertThat(job.claimAndRemind(b.id(), OffsetDateTime.now())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where template = 'EVENT_REMINDER'"
                + " and payload->>'bookingId' = ?", Integer.class, b.id().toString())).isZero();
    }
}
