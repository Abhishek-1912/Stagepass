package com.stagepass.organizer.service;

import com.stagepass.common.config.StagepassProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Stagepass owes an organizer (spec 8.4a). Each event's earnings (all ledger entries except payouts) become
 * payable {@code stagepass.payout.hold-days} after the event ends, or at once if it was cancelled; payouts and
 * event-less adjustments count against the payable amount, which may go negative (owed back, netted later).
 */
@Service
@Transactional(readOnly = true)
public class PayoutBalances {

    public record EventBalance(UUID eventId, String title, OffsetDateTime endsAt, long earnedPaise,
            OffsetDateTime payableFrom, boolean payable) {
    }

    public record Balances(long pendingPaise, long payablePaise, long paidOutPaise, long balancePaise, boolean negative,
            List<EventBalance> events) {
    }

    private final NamedParameterJdbcTemplate sql;
    private final Clock clock;
    private final int holdDays;

    public PayoutBalances(NamedParameterJdbcTemplate sql, Clock clock, StagepassProperties props) {
        this.sql = sql;
        this.clock = clock;
        this.holdDays = props.payout().holdDays();
    }

    public Balances forOrganizer(UUID organizerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        MapSqlParameterSource p = new MapSqlParameterSource("org", organizerId);
        List<EventBalance> events = sql.query("""
                select e.id, e.title, e.ends_at, e.status, sum(l.amount_paise) as earned
                from organizer_ledger_entries l join events e on e.id = l.event_id
                where l.organizer_id = :org and l.type <> 'PAYOUT'
                group by e.id, e.title, e.ends_at, e.status
                order by e.ends_at desc
                """, p, (rs, n) -> {
            OffsetDateTime endsAt = rs.getObject("ends_at", OffsetDateTime.class);
            boolean cancelled = "CANCELLED".equals(rs.getString("status"));
            OffsetDateTime from = cancelled ? null : endsAt.plusDays(holdDays);
            return new EventBalance(rs.getObject("id", UUID.class), rs.getString("title"), endsAt, rs.getLong("earned"),
                    from, cancelled || !now.isBefore(from));
        });
        Map<String, Object> other = sql.queryForMap("""
                select coalesce(sum(amount_paise) filter (where type = 'PAYOUT'), 0) as payouts,
                       coalesce(sum(amount_paise) filter (where type <> 'PAYOUT' and event_id is null), 0) as loose
                from organizer_ledger_entries where organizer_id = :org
                """, p);
        long payouts = ((Number) other.get("payouts")).longValue();
        long loose = ((Number) other.get("loose")).longValue();
        long payableEarned = events.stream().filter(EventBalance::payable).mapToLong(EventBalance::earnedPaise).sum();
        long pending = events.stream().filter(e -> !e.payable()).mapToLong(EventBalance::earnedPaise).sum();
        long payable = payableEarned + loose + payouts;
        return new Balances(pending, payable, -payouts, payable + pending, payable < 0, events);
    }
}
