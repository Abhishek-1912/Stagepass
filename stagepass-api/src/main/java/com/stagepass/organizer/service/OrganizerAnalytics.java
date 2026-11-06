package com.stagepass.organizer.service;

import com.stagepass.common.error.ApiException;
import com.stagepass.organizer.api.EventStatsResponse;
import com.stagepass.organizer.api.OverviewResponse;
import com.stagepass.organizer.service.DailySeries.DayPoint;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Organizer KPIs and sales analytics (plan 6 definitions): tickets sold = VALID/CHECKED_IN tickets; money comes from
 * the organizer ledger; daily sales bucket bookings by the IST date they were confirmed.
 */
@Service
@Transactional(readOnly = true)
public class OrganizerAnalytics {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String LIVE_TICKET = "t.status in ('VALID', 'CHECKED_IN')";

    private final NamedParameterJdbcTemplate sql;
    private final Clock clock;

    public OrganizerAnalytics(NamedParameterJdbcTemplate sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    public OverviewResponse overview(UUID organizerId, Integer daysParam) {
        int days = DailySeries.range(daysParam);
        MapSqlParameterSource p = new MapSqlParameterSource("org", organizerId).addValue("now", OffsetDateTime.now(clock));
        Map<String, Object> events = sql.queryForMap("""
                select count(*) filter (where status = 'PUBLISHED') as live,
                       count(*) filter (where status = 'PUBLISHED' and starts_at > :now) as upcoming
                from events where organizer_id = :org
                """, p);
        Integer sold = sql.queryForObject("select count(*) from tickets t join events e on e.id = t.event_id "
                + "where e.organizer_id = :org and " + LIVE_TICKET, p, Integer.class);
        Money money = money("organizer_id = :org", p);
        List<OverviewResponse.RecentBooking> recent = sql.query("""
                select b.id, b.event_id, b.code, e.title, b.attendee_name, b.total_paise, b.confirmed_at,
                       (select coalesce(sum(i.quantity), 0) from booking_items i where i.booking_id = b.id) as tickets
                from bookings b join events e on e.id = b.event_id
                where e.organizer_id = :org and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                order by b.confirmed_at desc limit 10
                """, p, (rs, n) -> new OverviewResponse.RecentBooking(rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class), rs.getString("code"), rs.getString("title"),
                rs.getString("attendee_name"), rs.getInt("tickets"), rs.getLong("total_paise"),
                rs.getObject("confirmed_at", OffsetDateTime.class)));
        return new OverviewResponse(num(events.get("live")), num(events.get("upcoming")), sold == null ? 0 : sold,
                money.gross, money.gross - money.refunded, money.earnings, recent,
                daily("e.organizer_id = :org", p, days));
    }

    public EventStatsResponse eventStats(UUID organizerId, UUID eventId, Integer daysParam) {
        int days = DailySeries.range(daysParam);
        MapSqlParameterSource p = new MapSqlParameterSource("org", organizerId).addValue("event", eventId);
        Integer owned = sql.queryForObject("select count(*) from events where id = :event and organizer_id = :org", p,
                Integer.class);
        if (owned == null || owned == 0) {
            throw ApiException.notFound("Event");
        }
        Map<UUID, int[]> live = new HashMap<>();
        Map<UUID, Long> liveAmount = new HashMap<>();
        int[] checkedIn = {0};
        sql.query("""
                select t.ticket_type_id, t.status, count(*) as n, sum(bi.unit_price_paise) as amount
                from tickets t join booking_items bi on bi.id = t.booking_item_id
                where t.event_id = :event and t.status in ('VALID', 'CHECKED_IN')
                group by t.ticket_type_id, t.status
                """, p, rs -> {
            UUID type = rs.getObject("ticket_type_id", UUID.class);
            int n = rs.getInt("n");
            live.computeIfAbsent(type, k -> new int[1])[0] += n;
            liveAmount.merge(type, rs.getLong("amount"), Long::sum);
            if ("CHECKED_IN".equals(rs.getString("status"))) {
                checkedIn[0] += n;
            }
        });
        List<EventStatsResponse.TypeStats> byType = sql.query("""
                select tt.id, tt.name, tt.price_paise, i.total, i.held
                from ticket_types tt join ticket_inventory i on i.ticket_type_id = tt.id
                where tt.event_id = :event
                order by tt.sort_order, tt.created_at
                """, p, (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new EventStatsResponse.TypeStats(id, rs.getString("name"), rs.getLong("price_paise"),
                    rs.getInt("total"), live.getOrDefault(id, new int[1])[0], rs.getInt("held"),
                    liveAmount.getOrDefault(id, 0L));
        });
        Money money = money("event_id = :event", p);
        return new EventStatsResponse(byType.stream().mapToInt(EventStatsResponse.TypeStats::total).sum(),
                byType.stream().mapToInt(EventStatsResponse.TypeStats::sold).sum(),
                byType.stream().mapToInt(EventStatsResponse.TypeStats::held).sum(), checkedIn[0], money.gross,
                money.refunded, money.gross - money.refunded, money.commission, byType,
                daily("b.event_id = :event", p, days));
    }

    private record Money(long gross, long refunded, long commission, long earnings) {
    }

    /** Ledger totals for the rows matching {@code where} (organizer- or event-scoped). */
    private Money money(String where, MapSqlParameterSource p) {
        Map<String, Object> m = sql.queryForMap("""
                select coalesce(sum(amount_paise) filter (where type = 'SALE'), 0) as gross,
                       coalesce(-sum(amount_paise) filter (where type = 'REFUND'), 0) as refunded,
                       coalesce(-sum(amount_paise) filter (where type = 'COMMISSION'), 0) as commission,
                       coalesce(sum(amount_paise) filter (where type <> 'PAYOUT'), 0) as earnings
                from organizer_ledger_entries where
                """ + where, p);
        return new Money(lng(m.get("gross")), lng(m.get("refunded")), lng(m.get("commission")), lng(m.get("earnings")));
    }

    private List<DayPoint> daily(String where, MapSqlParameterSource p, int days) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        p.addValue("from", today.minusDays(days - 1L).atStartOfDay(IST).toOffsetDateTime());
        Map<LocalDate, DayPoint> found = new HashMap<>();
        sql.query("""
                select (b.confirmed_at at time zone 'Asia/Kolkata')::date as day,
                       sum((select coalesce(sum(i.quantity), 0) from booking_items i where i.booking_id = b.id)) as tickets,
                       sum(b.subtotal_paise) as amount
                from bookings b join events e on e.id = b.event_id
                where b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED') and b.confirmed_at >= :from and
                """ + where + " group by day", p, rs -> {
            LocalDate d = rs.getObject("day", LocalDate.class);
            found.put(d, new DayPoint(d, rs.getInt("tickets"), rs.getLong("amount")));
        });
        return DailySeries.fill(found, today, days);
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static long lng(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
