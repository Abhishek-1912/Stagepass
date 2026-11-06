package com.stagepass.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Gives back tickets held by checkouts that ran out of time. Bookings whose payment is already captured are left
 * for the confirmation path. Rows locked by a confirmation in progress are skipped and retried next run.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final BookingExpiryService expiry;
    private final TransactionTemplate tx;

    public HoldExpiryJob(JdbcTemplate jdbc, BookingExpiryService expiry, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.expiry = expiry;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    void scheduled() {
        int n = expireDue();
        if (n > 0) {
            log.info("Expired {} unpaid bookings", n);
        }
    }

    /** Returns how many bookings were expired. */
    public int expireDue() {
        int total = 0;
        int[] batch = new int[2]; // [rows locked, bookings expired]
        do {
            tx.executeWithoutResult(s -> {
                List<UUID> ids = jdbc.queryForList("""
                        select b.id from bookings b
                        where b.status = 'PENDING_PAYMENT' and b.hold_expires_at < now()
                          and not exists (select 1 from payments p where p.booking_id = b.id and p.status = 'CAPTURED')
                        order by b.hold_expires_at
                        limit ?
                        for update of b skip locked
                        """, UUID.class, BATCH);
                int expired = 0;
                for (UUID id : ids) {
                    if (expiry.expire(id, "HOLD_EXPIRED")) {
                        expired++;
                    }
                }
                batch[0] = ids.size();
                batch[1] = expired;
            });
            total += batch[1];
        } while (batch[0] == BATCH);
        return total;
    }
}
