package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.ReminderJob;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.catalog.service.EventChangeHandler;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderJobTest extends PostgresIntegrationTest {

    @Autowired
    ReminderJob job;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    List<EventChangeHandler> changeHandlers;

    @Autowired
    EventRepository events;

    /** Moves the event to start {@code hours} from now (3 h long). */
    void startsIn(UUID eventId, double hours) {
        jdbc.update("update events set starts_at = now() + make_interval(secs => ?), ends_at = now() + make_interval(secs => ?)"
                + " where id = ?", hours * 3600, hours * 3600 + 3 * 3600, eventId);
    }

    int emails(UUID bookingId) {
        return jdbc.queryForObject("select count(*) from email_outbox where template = 'EVENT_REMINDER' and payload->>'bookingId' = ?",
                Integer.class, bookingId.toString());
    }

    int notes(TestUser user) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'EVENT_REMINDER'",
                Integer.class, user.id());
    }

    @Test
    void remindsOnceInsideTheWindow() {
        TestUser buyer = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, buyer);
        startsIn(e.eventId(), 20);

        job.run();
        assertThat(emails(b.id())).isEqualTo(1);
        assertThat(notes(buyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_address from email_outbox where template = 'EVENT_REMINDER'"
                + " and payload->>'bookingId' = ?", String.class, b.id().toString())).isEqualTo("asha@test.in");
        assertThat(jdbc.queryForObject("select reminder_sent_at is not null from bookings where id = ?", Boolean.class, b.id())).isTrue();

        job.run();
        assertThat(emails(b.id())).isEqualTo(1);
        assertThat(notes(buyer)).isEqualTo(1);
    }

    @Test
    void outsideTheWindowOrNotLiveIsSkipped() {
        LiveEvent far = support.liveEvent(49900, 20);
        BookingResponse farBooking = support.confirmed(far, 1, users.attendee());
        startsIn(far.eventId(), 30);

        LiveEvent soon = support.liveEvent(49900, 20);
        BookingResponse soonBooking = support.confirmed(soon, 1, users.attendee());
        startsIn(soon.eventId(), 0.5);

        LiveEvent cancelled = support.liveEvent(49900, 20);
        BookingResponse cancelledBooking = support.confirmed(cancelled, 1, users.attendee());
        startsIn(cancelled.eventId(), 20);
        jdbc.update("update events set status = 'CANCELLED' where id = ?", cancelled.eventId());

        LiveEvent refunded = support.liveEvent(49900, 20);
        BookingResponse refundedBooking = support.confirmed(refunded, 1, users.attendee());
        startsIn(refunded.eventId(), 20);
        jdbc.update("update tickets set status = 'REFUNDED' where booking_id = ?", refundedBooking.id());
        jdbc.update("update bookings set status = 'REFUNDED' where id = ?", refundedBooking.id());

        LiveEvent unpaid = support.liveEvent(49900, 20);
        BookingResponse unpaidBooking = support.book(unpaid, 1, users.attendee());
        startsIn(unpaid.eventId(), 20);

        job.run();
        for (BookingResponse b : List.of(farBooking, soonBooking, cancelledBooking, refundedBooking, unpaidBooking)) {
            assertThat(emails(b.id())).as(b.code()).isZero();
        }
    }

    @Test
    void emailPreferenceOffStillNotifiesInApp() {
        TestUser buyer = users.attendee();
        jdbc.update("update users set email_reminders = false where id = ?", buyer.id());
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, buyer);
        startsIn(e.eventId(), 20);

        job.run();
        assertThat(emails(b.id())).isZero();
        assertThat(notes(buyer)).isEqualTo(1);
    }

    @Test
    void rescheduleGetsANewReminder() {
        TestUser buyer = users.attendee();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, buyer);
        startsIn(e.eventId(), 20);
        job.run();
        assertThat(emails(b.id())).isEqualTo(1);

        startsIn(e.eventId(), 10);
        changeHandlers.forEach(h -> h.onEventChanged(events.findById(e.eventId()).orElseThrow()));
        job.run();
        assertThat(emails(b.id())).isEqualTo(2);
        assertThat(notes(buyer)).isEqualTo(2);

        job.run();
        assertThat(emails(b.id())).isEqualTo(2);
    }
}
