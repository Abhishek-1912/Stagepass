package com.stagepass.admin;

import com.stagepass.admin.api.AdminOverview;
import com.stagepass.admin.service.PlatformAnalytics;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.organizer.service.DailySeries.DayPoint;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The overview is platform-wide, so these tests compare before/after deltas (tests run one at a time). */
class PlatformAnalyticsTest extends PostgresIntegrationTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    PlatformAnalytics analytics;

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private long userCount() {
        return jdbc.queryForObject("select count(*) from users", Long.class);
    }

    @Test
    void overviewMoneyAndCounts() {
        AdminOverview before = analytics.overview(30);
        long usersBefore = userCount();

        LiveEvent e = support.liveEvent(100000, 20);
        LiveEvent cheap = support.liveEvent(e.organizer(), 50000, 20);
        BookingResponse a = support.confirmed(e, 1, users.attendee());
        BookingResponse b = support.confirmed(cheap, 1, users.attendee());
        jdbc.update("update payments set gateway_fee_paise = 2000, gateway_tax_paise = 360 where booking_id = ?", a.id());
        jdbc.update("update payments set gateway_fee_paise = 1000, gateway_tax_paise = 180 where booking_id = ?", b.id());
        UUID payment = jdbc.queryForObject("select id from payments where booking_id = ?", UUID.class, b.id());
        jdbc.update("""
                insert into refunds (payment_id, booking_id, amount_paise, status, kind) values (?, ?, 50000, 'PROCESSED', 'ADMIN')
                """, payment, b.id());
        jdbc.update("""
                insert into refunds (payment_id, booking_id, amount_paise, status, kind, needs_retry) values (?, ?, 100, 'FAILED', 'ADMIN', true)
                """, payment, b.id());
        jdbc.update("insert into disputes (payment_id, razorpay_dispute_id, amount_paise, status) values (?, ?, 100, 'open')",
                payment, "disp_" + UUID.randomUUID());
        users.pendingOrganizer();
        UUID pendingEvent = catalog.draftEvent(e.organizer());
        jdbc.update("update events set status = 'PENDING_REVIEW' where id = ?", pendingEvent);
        jdbc.update("insert into event_reports (event_id, reporter_id, reason) values (?, ?, 'SCAM')", e.eventId(),
                users.attendee().id());

        AdminOverview after = analytics.overview(30);

        long fees = a.price().feePaise() + a.price().commissionPaise() + b.price().feePaise() + b.price().commissionPaise();
        assertThat(after.gmvPaise() - before.gmvPaise()).isEqualTo(a.price().totalPaise() + b.price().totalPaise());
        assertThat(after.platformRevenuePaise() - before.platformRevenuePaise()).isEqualTo(fees - 3540);
        assertThat(after.refundsPaise() - before.refundsPaise()).isEqualTo(50000);
        assertThat(after.bookings() - before.bookings()).isEqualTo(2);
        assertThat(after.ticketsSold() - before.ticketsSold()).isEqualTo(2);
        assertThat(after.pendingApplications() - before.pendingApplications()).isEqualTo(1);
        assertThat(after.pendingEvents() - before.pendingEvents()).isEqualTo(1);
        assertThat(after.openReports() - before.openReports()).isEqualTo(1);
        assertThat(after.failedRefunds() - before.failedRefunds()).isEqualTo(1);
        assertThat(after.openDisputes() - before.openDisputes()).isEqualTo(1);
        assertThat(after.liveEvents() - before.liveEvents()).isEqualTo(2);
        assertThat(after.newUsers() - before.newUsers()).isEqualTo(userCount() - usersBefore);
        assertThat(after.totalUsers()).isEqualTo(userCount());
    }

    @Test
    void dailyBucketsInIst() throws Exception {
        LocalDate yesterday = LocalDate.now(IST).minusDays(1);
        long dayBefore = point(analytics.overview(7), yesterday).amountPaise();

        BookingResponse b = support.confirmed(support.liveEvent(49900, 20), 1, users.attendee());
        jdbc.update("update bookings set confirmed_at = ?::timestamptz where id = ?", yesterday + " 00:15:00+05:30", b.id());

        AdminOverview after = analytics.overview(7);
        assertThat(after.daily()).hasSize(7);
        assertThat(after.daily().get(6).date()).isEqualTo(LocalDate.now(IST));
        assertThat(point(after, yesterday).amountPaise() - dayBefore).isEqualTo(b.price().totalPaise());

        mvc.perform(get("/api/v1/admin/overview").param("days", "45").header("Authorization", users.admin().bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("days"));
        mvc.perform(get("/api/v1/admin/overview").param("days", "90").header("Authorization", users.admin().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daily.length()").value(90));
        mvc.perform(get("/api/v1/admin/overview").header("Authorization", users.attendee().bearer()))
                .andExpect(status().isForbidden());
    }

    private static DayPoint point(AdminOverview o, LocalDate day) {
        return o.daily().stream().filter(p -> p.date().equals(day)).findFirst().orElseThrow();
    }
}
