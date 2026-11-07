package com.stagepass.admin;

import com.stagepass.admin.api.RecordPayoutRequest;
import com.stagepass.admin.service.AdminPayoutService;
import com.stagepass.organizer.service.PayoutBalances;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPayoutTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AdminPayoutService payouts;

    @Autowired
    PayoutBalances balances;

    @Test
    void parallelPayoutsNeverOverpay() throws Exception {
        TestUser admin = users.admin();
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.publishedEvent(org, "Paid gig", OffsetDateTime.now().minusDays(5), "Paycity", 100);
        jdbc.update("insert into organizer_ledger_entries (organizer_id, event_id, type, amount_paise) values (?, ?, 'SALE', 50000)",
                org.organizerId(), event);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String ref = "UTR-PAR-" + i;
            results.add(pool.submit(() -> {
                go.await();
                try {
                    payouts.record(admin.id(), org.organizerId(), new RecordPayoutRequest(30_000L, ref, today, null));
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get(60, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(balances.forOrganizer(org.organizerId()).payablePaise()).isEqualTo(20_000L);
        assertThat(jdbc.queryForObject("select count(*) from payouts where organizer_id = ?", Integer.class, org.organizerId()))
                .isEqualTo(1);
    }
}
