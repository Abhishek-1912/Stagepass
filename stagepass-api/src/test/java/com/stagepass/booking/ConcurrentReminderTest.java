package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.service.ReminderJob;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentReminderTest extends PostgresIntegrationTest {

    @Autowired
    ReminderJob job;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void overlappingRunsSendOnce() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        List<UUID> bookings = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            bookings.add(support.confirmed(e, 1, users.attendee()).id());
        }
        jdbc.update("update events set starts_at = now() + interval '20 hours', ends_at = now() + interval '23 hours' where id = ?",
                e.eventId());

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(pool.submit(() -> {
                go.await();
                return job.run();
            }));
        }
        go.countDown();
        int total = 0;
        for (Future<Integer> f : runs) {
            total += f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(total).isEqualTo(5);
        for (UUID b : bookings) {
            assertThat(jdbc.queryForObject("select count(*) from email_outbox where template = 'EVENT_REMINDER'"
                    + " and payload->>'bookingId' = ?", Integer.class, b.toString())).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from notifications n join bookings b on b.user_id = n.user_id"
                + " where n.type = 'EVENT_REMINDER' and b.event_id = ?", Integer.class, e.eventId())).isEqualTo(5);
    }
}
