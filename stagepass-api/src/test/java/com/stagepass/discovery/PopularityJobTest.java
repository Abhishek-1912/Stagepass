package com.stagepass.discovery;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.discovery.service.PopularityJob;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PopularityJobTest extends PostgresIntegrationTest {

    @Autowired
    PopularityJob job;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private void confirmedDaysAgo(LiveEvent e, int qty, int days) {
        BookingResponse b = support.book(e, qty, users.attendee());
        assertThat(b.status()).isEqualTo("CONFIRMED");
        jdbc.update("update bookings set confirmed_at = now() - make_interval(days => ?) where id = ?", days, b.id());
    }

    @Test
    void popularityWeightsRecentSales() {
        LiveEvent e = support.liveEvent(0, 100);
        confirmedDaysAgo(e, 2, 0);
        confirmedDaysAgo(e, 4, 10);
        confirmedDaysAgo(e, 9, 40);
        LiveEvent quiet = support.liveEvent(0, 100);
        jdbc.update("update events set popularity_score = 99 where id = ?", quiet.eventId());

        assertThat(job.recompute()).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForObject("select popularity_score from events where id = ?", Double.class, e.eventId()))
                .isEqualTo(4.0);
        assertThat(jdbc.queryForObject("select popularity_score from events where id = ?", Double.class, quiet.eventId()))
                .isZero();
    }

    @Test
    void savesAddHalfATicket() {
        LiveEvent e = support.liveEvent(0, 100);
        confirmedDaysAgo(e, 2, 0);
        jdbc.update("insert into saved_events (user_id, event_id) values (?, ?), (?, ?)", users.attendee().id(), e.eventId(),
                users.attendee().id(), e.eventId());
        UUID old = users.attendee().id();
        jdbc.update("insert into saved_events (user_id, event_id, created_at) values (?, ?, now() - interval '40 days')", old,
                e.eventId());

        job.recompute();

        assertThat(jdbc.queryForObject("select popularity_score from events where id = ?", Double.class, e.eventId()))
                .isEqualTo(3.0);
    }
}
