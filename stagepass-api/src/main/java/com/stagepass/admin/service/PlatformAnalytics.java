package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminOverview;
import com.stagepass.organizer.service.DailySeries;
import com.stagepass.organizer.service.DailySeries.DayPoint;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Platform-wide numbers (spec 9 admin Overview). GMV counts bookings confirmed in the range (even if later refunded);
 * platform revenue = convenience fee + commission − Razorpay fee and its tax (the fee's GST is tax, not revenue).
 */
@Service
public class PlatformAnalytics {

    private static final String SOLD = "b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED')"
            + " and b.confirmed_at >= :from";

    private final NamedParameterJdbcTemplate sql;
    private final Clock clock;

    public PlatformAnalytics(NamedParameterJdbcTemplate sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminOverview overview(Integer daysParam) {
        int days = DailySeries.range(daysParam);
        LocalDate today = LocalDate.now(clock.withZone(AdminQueries.IST));
        OffsetDateTime from = AdminQueries.startOf(today.minusDays(days - 1L));
        MapSqlParameterSource p = new MapSqlParameterSource("from", from).addValue("now", OffsetDateTime.now(clock));

        Map<String, Object> money = sql.queryForMap("""
                select coalesce(sum(b.total_paise), 0) as gmv,
                       coalesce(sum(b.fee_paise + b.commission_paise), 0) as fees,
                       coalesce(sum((select coalesce(sum(coalesce(p.gateway_fee_paise, 0) + coalesce(p.gateway_tax_paise, 0)), 0)
                                     from payments p where p.booking_id = b.id and p.status in ('CAPTURED', 'REFUNDED'))), 0) as gateway,
                       count(*) as bookings,
                       coalesce(sum((select coalesce(sum(i.quantity), 0) from booking_items i where i.booking_id = b.id)), 0)
                           as tickets
                from bookings b where
                """ + SOLD, p);
        Map<String, Object> counts = sql.queryForMap("""
                select (select coalesce(sum(amount_paise), 0) from refunds where status = 'PROCESSED' and updated_at >= :from)
                           as refunds,
                       (select count(*) from users where created_at >= :from) as new_users,
                       (select count(*) from users) as total_users,
                       (select count(*) from events where status = 'PUBLISHED' and ends_at > :now) as live_events,
                       (select count(*) from organizers where status = 'PENDING') as pending_applications,
                       (select count(*) from events where status = 'PENDING_REVIEW') as pending_events,
                       (select count(*) from event_reports where status = 'OPEN') as open_reports,
                       (select count(*) from refunds where status = 'FAILED' and needs_retry) as failed_refunds,
                       (select count(*) from disputes where lower(status) not in ('won', 'lost', 'closed')) as open_disputes
                """, p);

        Map<LocalDate, DayPoint> found = new HashMap<>();
        sql.query("""
                select (b.confirmed_at at time zone 'Asia/Kolkata')::date as day,
                       sum((select coalesce(sum(i.quantity), 0) from booking_items i where i.booking_id = b.id)) as tickets,
                       sum(b.total_paise) as amount
                from bookings b where
                """ + SOLD + " group by day", p, rs -> {
            LocalDate d = rs.getObject("day", LocalDate.class);
            found.put(d, new DayPoint(d, rs.getInt("tickets"), rs.getLong("amount")));
        });

        return new AdminOverview(days, lng(money.get("gmv")), lng(money.get("fees")) - lng(money.get("gateway")),
                lng(counts.get("refunds")), lng(money.get("bookings")), lng(money.get("tickets")),
                lng(counts.get("new_users")), lng(counts.get("total_users")), lng(counts.get("live_events")),
                lng(counts.get("pending_applications")), lng(counts.get("pending_events")), lng(counts.get("open_reports")),
                lng(counts.get("failed_refunds")), lng(counts.get("open_disputes")), DailySeries.fill(found, today, days));
    }

    private static long lng(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
