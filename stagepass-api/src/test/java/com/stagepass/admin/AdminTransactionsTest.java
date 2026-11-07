package com.stagepass.admin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTransactionsTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    private record Paid(BookingResponse booking, UUID paymentId, String razorpayPaymentId, String orderId) {
    }

    private Paid paid(LiveEvent e) {
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        var row = jdbc.queryForMap("select id, razorpay_payment_id, razorpay_order_id from payments where booking_id = ?", b.id());
        return new Paid(b, (UUID) row.get("id"), (String) row.get("razorpay_payment_id"), (String) row.get("razorpay_order_id"));
    }

    @Test
    void paymentSearchByCodeEmailAndRazorpayIds() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        Paid p = paid(e);
        Paid other = paid(e);
        String email = "buyer-" + UUID.randomUUID().toString().substring(0, 8) + "@stagepass.test";
        jdbc.update("update bookings set attendee_email = ? where id = ?", email, p.booking().id());

        for (String q : new String[] {"  " + p.razorpayPaymentId() + " ", p.orderId(), p.booking().code().toLowerCase(),
                email.toUpperCase()}) {
            mvc.perform(get("/api/v1/admin/payments").param("q", q).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", hasItem(p.paymentId().toString())))
                    .andExpect(jsonPath("$.items[*].id", not(hasItem(other.paymentId().toString()))));
        }
        mvc.perform(get("/api/v1/admin/payments").param("q", p.booking().code()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[0].bookingCode").value(p.booking().code()))
                .andExpect(jsonPath("$.items[0].status").value("CAPTURED"))
                .andExpect(jsonPath("$.items[0].amountPaise").value(p.booking().price().totalPaise()));
        mvc.perform(get("/api/v1/admin/payments").param("q", "%_'").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/payments").param("status", "LOST").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void dateRangeIsIstInclusiveAndBounded() throws Exception {
        TestUser admin = users.admin();
        Paid p = paid(support.liveEvent(49900, 20));
        jdbc.update("update payments set created_at = '2026-12-01 23:30:00+05:30' where id = ?", p.paymentId());

        mvc.perform(get("/api/v1/admin/payments").param("from", "2026-12-01").param("to", "2026-12-01")
                        .param("q", p.booking().code()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/admin/payments").param("from", "2026-12-02").param("q", p.booking().code())
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/admin/payments").param("from", "2026-12-02").param("to", "2026-12-01")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("to"));
        mvc.perform(get("/api/v1/admin/payments").param("from", "2025-01-01").param("to", "2026-02-05")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    @Test
    void refundsListShowsRetryState() throws Exception {
        TestUser admin = users.admin();
        Paid p = paid(support.liveEvent(49900, 20));
        UUID refund = jdbc.queryForObject("""
                insert into refunds (payment_id, booking_id, amount_paise, reason, status, kind, attempts, needs_retry,
                                     failure_reason, ticket_ids)
                values (?, ?, 49900, 'test', 'FAILED', 'ADMIN', 3, true, 'Gateway timeout', array[]::uuid[]) returning id
                """, UUID.class, p.paymentId(), p.booking().id());

        mvc.perform(get("/api/v1/admin/refunds").param("status", "FAILED").param("q", p.booking().code())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(refund.toString()))
                .andExpect(jsonPath("$.items[0].attempts").value(3))
                .andExpect(jsonPath("$.items[0].needsRetry").value(true))
                .andExpect(jsonPath("$.items[0].failureReason").value("Gateway timeout"))
                .andExpect(jsonPath("$.items[0].kind").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/refunds").param("status", "PROCESSED").param("q", p.booking().code())
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void disputesList() throws Exception {
        TestUser admin = users.admin();
        Paid p = paid(support.liveEvent(49900, 20));
        String disputeId = "disp_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into disputes (payment_id, razorpay_dispute_id, amount_paise, reason, status) values (?, ?, 49900, 'fraud', 'open')",
                p.paymentId(), disputeId);

        mvc.perform(get("/api/v1/admin/disputes").param("size", "100").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.razorpayDisputeId == '" + disputeId + "')].bookingCode")
                        .value(hasItem(p.booking().code())));
        mvc.perform(get("/api/v1/admin/disputes").param("status", "won").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].razorpayDisputeId", not(hasItem(disputeId))));
    }

    @Test
    void csvExportHasBomHeaderGuardAndAudit() throws Exception {
        TestUser admin = users.admin();
        Paid p = paid(support.liveEvent(123450, 20));
        jdbc.update("update payments set created_at = '2026-12-03 10:00:00+05:30', amount_paise = 123450,"
                + " gateway_fee_paise = 2469, gateway_tax_paise = 444 where id = ?", p.paymentId());
        jdbc.update("update bookings set attendee_email = '=cmd@x.test' where id = ?", p.booking().id());

        byte[] body = mvc.perform(get("/api/v1/admin/payments/export").param("from", "2026-12-03").param("to", "2026-12-03")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"stagepass-payments-2026-12-03-to-2026-12-03.csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Date (IST),Booking code,Event,Attendee email,Amount (Rs.),Status,Method,"
                + "Razorpay payment id,Razorpay order id,Gateway fee (Rs.),Gateway tax (Rs.),Refunded (Rs.)\r\n");
        assertThat(csv).contains("03-12-2026 10:00").contains(p.booking().code()).contains(",1234.50,")
                .contains(",24.69,4.44,0.00").contains("'=cmd@x.test");
        String rows = jdbc.queryForObject("select after->>'rows' from audit_logs where action = 'TRANSACTIONS_EXPORTED'"
                + " and actor_id = ?", String.class, admin.id());
        assertThat(Integer.parseInt(rows)).isEqualTo(csv.split("\r\n").length - 1);
        mvc.perform(get("/api/v1/admin/payments/export").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminGets403() throws Exception {
        TestUser org = users.approvedOrganizer();
        for (String path : new String[] {"/payments", "/refunds", "/disputes", "/payments/export"}) {
            mvc.perform(get("/api/v1/admin" + path).header("Authorization", org.bearer())).andExpect(status().isForbidden());
        }
    }
}
