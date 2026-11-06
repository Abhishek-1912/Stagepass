package com.stagepass.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Keeps refunds moving without anyone watching:
 * <ul>
 *   <li>unanswered refund calls are reconciled with Razorpay (found → recorded, missing → sent again);</li>
 *   <li>refused refunds whose money is still owed are retried with growing gaps (up to 8 attempts);</li>
 *   <li>cancelled events still holding live tickets or open checkouts are swept again.</li>
 * </ul>
 */
@Component
public class RefundMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(RefundMaintenanceJob.class);
    static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbc;
    private final RefundGateway gateway;
    private final RefundService refunds;

    public RefundMaintenanceJob(JdbcTemplate jdbc, RefundGateway gateway, RefundService refunds) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.refunds = refunds;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    void scheduled() {
        run();
    }

    /** Returns how many refunds or events were acted on. */
    public int run() {
        return reconcileUnanswered(Duration.ofMinutes(2)) + retryRefused(Duration.ofMinutes(10)) + sweepCancelledEvents();
    }

    /** Refund calls left unanswered for at least {@code minAge}. */
    public int reconcileUnanswered(Duration minAge) {
        List<UUID> ids = jdbc.queryForList("""
                select id from refunds
                where status = 'PROCESSING' and razorpay_refund_id is null and payment_id is not null
                  and updated_at <= now() - make_interval(secs => ?)
                order by updated_at limit 100
                """, UUID.class, minAge.toSeconds());
        ids.forEach(id -> each("reconcile refund " + id, () -> gateway.reconcile(id)));
        return ids.size();
    }

    /** Refused refunds, waiting {@code gap} × attempts between tries. */
    public int retryRefused(Duration gap) {
        List<UUID> ids = jdbc.queryForList("""
                select id from refunds
                where status = 'FAILED' and needs_retry and attempts < ?
                  and updated_at <= now() - make_interval(secs => ? * attempts)
                order by updated_at limit 100
                """, UUID.class, MAX_ATTEMPTS, gap.toSeconds());
        ids.forEach(id -> each("retry refund " + id, () -> refunds.retry(id)));
        return ids.size();
    }

    public int sweepCancelledEvents() {
        List<UUID> events = jdbc.queryForList("""
                select distinct b.event_id from bookings b join events e on e.id = b.event_id
                where e.status = 'CANCELLED'
                  and (b.status = 'PENDING_PAYMENT'
                       or (b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                           and exists (select 1 from tickets t where t.booking_id = b.id and t.status = 'VALID')))
                limit 50
                """, UUID.class);
        events.forEach(id -> each("refund cancelled event " + id, () -> refunds.refundEventCancellation(id, null)));
        return events.size();
    }

    private static void each(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("Could not {}", what, e);
        }
    }
}
