package com.stagepass.checkin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.checkin.api.ScanRequest;
import com.stagepass.checkin.api.ScanResponse;
import com.stagepass.checkin.service.CheckInService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentScanTest extends PostgresIntegrationTest {

    @Autowired
    CheckInService checkIn;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoScannersOneWinner() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        String token = b.tickets().get(0).qrToken();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<ScanResponse>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return checkIn.scan(e.organizer().id(), new ScanRequest(e.eventId(), null, token, null));
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<ScanResponse> f : results) {
            outcomes.add(f.get().result());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("VALID"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("ALREADY_USED"::equals).hasSize(7);
        assertThat(jdbc.queryForObject("select count(*) from check_ins where ticket_id = ?", Integer.class,
                b.tickets().get(0).id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from scan_logs where event_id = ?", Integer.class, e.eventId()))
                .isEqualTo(8);
    }
}
