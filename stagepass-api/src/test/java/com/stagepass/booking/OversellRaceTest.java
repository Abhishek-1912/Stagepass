package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.CreateBookingRequest;
import com.stagepass.booking.service.BookingService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;

class OversellRaceTest extends PostgresIntegrationTest {

    @Autowired
    BookingService bookings;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sixteenBuyersForTenTickets() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        List<TestUsers.TestUser> buyers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            buyers.add(users.attendee());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<String>> results = new ArrayList<>();
        for (TestUsers.TestUser buyer : buyers) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    bookings.create(buyer.id(), "race-" + UUID.randomUUID(), new CreateBookingRequest(e.eventId(),
                            List.of(new CreateBookingRequest.Item(e.ticketTypeId(), 1)), "Buyer", "buyer@test.in", null, null));
                    return "OK";
                } catch (ApiException ex) {
                    return ex.code().name();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(10);
        assertThat(outcomes).filteredOn(ErrorCode.SOLD_OUT.name()::equals).hasSize(6);
        assertThat(jdbc.queryForMap("select total, sold, held from ticket_inventory where ticket_type_id = ?", e.ticketTypeId()))
                .containsEntry("held", 10).containsEntry("sold", 0).containsEntry("total", 10);
    }
}
