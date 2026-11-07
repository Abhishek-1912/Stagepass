package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Reminds ticket holders 24 hours before their event (spec 8.9): one email (unless switched off) and one in-app
 * notification per booking. Each booking is claimed in its own transaction with {@code skip locked}, so overlapping
 * runs never remind the same booking twice; the dedupe key holds the start time, so a rescheduled event gets a fresh
 * reminder.
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int BATCH = 200;

    private final NamedParameterJdbcTemplate sql;
    private final BookingRepository bookings;
    private final EventRepository events;
    private final BookingEmails emails;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ReminderJob(NamedParameterJdbcTemplate sql, BookingRepository bookings, EventRepository events,
            BookingEmails emails, Clock clock, PlatformTransactionManager txManager) {
        this.sql = sql;
        this.bookings = bookings;
        this.events = events;
        this.emails = emails;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron = "0 */15 * * * *")
    void scheduled() {
        run();
    }

    /** Returns how many bookings were reminded by this run. */
    public int run() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<UUID> candidates = sql.queryForList(CANDIDATES + " limit " + BATCH, window(now), UUID.class);
        int reminded = 0;
        for (UUID id : candidates) {
            try {
                Boolean done = tx.execute(s -> remind(id, now));
                if (Boolean.TRUE.equals(done)) {
                    reminded++;
                }
            } catch (RuntimeException e) {
                log.error("Reminder for booking {} failed; the next run retries", id, e);
            }
        }
        return reminded;
    }

    /** Claims and reminds one booking in its own transaction; false when it was skipped. */
    boolean claimAndRemind(UUID bookingId, OffsetDateTime now) {
        return Boolean.TRUE.equals(tx.execute(s -> remind(bookingId, now)));
    }

    private static final String CANDIDATES = """
            select b.id from bookings b join events e on e.id = b.event_id
            where b.reminder_sent_at is null and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
              and e.status = 'PUBLISHED' and e.starts_at > :from and e.starts_at <= :to
              and exists (select 1 from tickets t where t.booking_id = b.id and t.status = 'VALID')
            order by e.starts_at, b.id
            """;

    private static MapSqlParameterSource window(OffsetDateTime now) {
        return new MapSqlParameterSource("from", now.plusHours(1)).addValue("to", now.plusHours(24));
    }

    /** Claims the booking (skipping it if another run holds it or already sent it) and sends the reminder. */
    private boolean remind(UUID bookingId, OffsetDateTime now) {
        List<UUID> claimed = sql.queryForList("""
                select b.id from bookings b join events e on e.id = b.event_id
                where b.id = :id and b.reminder_sent_at is null and e.status = 'PUBLISHED'
                  and e.starts_at > :from and e.starts_at <= :to
                  and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                  and exists (select 1 from tickets t where t.booking_id = b.id and t.status = 'VALID')
                for update of b skip locked
                """, window(now).addValue("id", bookingId), UUID.class);
        if (claimed.isEmpty()) {
            return false;
        }
        sql.update("update bookings set reminder_sent_at = :now where id = :id",
                new MapSqlParameterSource("now", now).addValue("id", bookingId));
        Booking b = bookings.findById(bookingId).orElseThrow();
        Event e = events.findById(b.getEventId()).orElseThrow();
        Boolean wantsEmail = sql.queryForObject("select coalesce((select email_reminders from users where id = :user), true)",
                new MapSqlParameterSource("user", b.getUserId()), Boolean.class);
        LocalDate eventDay = e.getStartsAt().atZoneSameInstant(IST).toLocalDate();
        String day = eventDay.isAfter(now.atZoneSameInstant(IST).toLocalDate()) ? "Tomorrow" : "Today";
        String key = "event-reminder:" + bookingId + ":" + e.getStartsAt().toEpochSecond();
        emails.reminder(b, e, day, key, !Boolean.FALSE.equals(wantsEmail));
        return true;
    }
}
