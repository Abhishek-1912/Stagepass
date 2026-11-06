package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.HoldExpiryJob;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HoldExpiryJobTest extends PostgresIntegrationTest {

    @Autowired
    HoldExpiryJob job;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private String status(BookingResponse b) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id());
    }

    private void overdue(BookingResponse b) {
        jdbc.update("update bookings set hold_expires_at = now() - interval '1 minute' where id = ?", b.id());
    }

    @Test
    void expiresDueHoldsAndReleasesInventory() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.book(e, 3, users.attendee());
        overdue(b);

        assertThat(job.expireDue()).isGreaterThanOrEqualTo(1);

        assertThat(status(b)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select held from ticket_inventory where ticket_type_id = ?", Integer.class,
                e.ticketTypeId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'BOOKING_EXPIRED' and entity_id = ?",
                Integer.class, b.id().toString())).isEqualTo(1);
    }

    @Test
    void keepsUnexpiredAndCapturedBookings() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse fresh = support.book(e, 1, users.attendee());
        BookingResponse captured = support.book(e, 1, users.attendee());
        overdue(captured);
        jdbc.update("update payments set status = 'CAPTURED' where booking_id = ?", captured.id());

        job.expireDue();

        assertThat(status(fresh)).isEqualTo("PENDING_PAYMENT");
        assertThat(status(captured)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select held from ticket_inventory where ticket_type_id = ?", Integer.class,
                e.ticketTypeId())).isEqualTo(2);
    }
}
