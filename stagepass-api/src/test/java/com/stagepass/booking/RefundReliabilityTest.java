package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.BookingExpiryService;
import com.stagepass.booking.service.RefundMaintenanceJob;
import com.stagepass.payment.service.PaymentService;
import com.stagepass.support.FakeRazorpayServer;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refunds and cancellations when Razorpay times out, refuses, or the seats were resold (final-review findings). */
class RefundReliabilityTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RefundMaintenanceJob job;

    @Autowired
    PaymentService payments;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    PlatformTransactionManager txManager;

    private final FakeRazorpayServer rzp = SharedRazorpay.server();

    @AfterEach
    void clear() {
        rzp.clearFailures();
    }

    private record Sold(LiveEvent event, TestUser buyer, BookingResponse booking, String paymentId) {
    }

    private Sold sold(int total, int qty, Integer cutoffHours) {
        LiveEvent e = support.liveEvent(49900, total);
        if (cutoffHours != null) {
            jdbc.update("update events set refund_policy = 'UNTIL_HOURS_BEFORE', refund_cutoff_hours = ? where id = ?",
                    cutoffHours, e.eventId());
        }
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(e, qty, buyer);
        String pay = jdbc.queryForObject("select razorpay_payment_id from payments where booking_id = ?", String.class, b.id());
        return new Sold(e, buyer, b, pay);
    }

    private int refundAll(Sold s) throws Exception {
        return mvc.perform(post("/api/v1/me/bookings/{id}/refunds", s.booking().id()).header("Authorization", s.buyer().bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
    }

    private Map<String, Object> refund(Sold s) {
        return jdbc.queryForMap("""
                select status, razorpay_refund_id, needs_retry, kind from refunds where booking_id = ?
                order by created_at desc limit 1
                """, s.booking().id());
    }

    private long refundPosts(String paymentId) {
        return rzp.count(r -> r.method().equals("POST") && r.path().equals("/v1/payments/" + paymentId + "/refund"));
    }

    private String ticketStatus(Sold s) {
        return jdbc.queryForObject("select string_agg(distinct status, ',') from tickets where booking_id = ?", String.class,
                s.booking().id());
    }

    @Test
    void unansweredRefundIsReconciledNotRepeated() throws Exception {
        Sold s = sold(10, 2, 48);
        rzp.loseRefundReplies(s.paymentId());

        assertThat(refundAll(s)).isEqualTo(200);
        assertThat(refund(s)).containsEntry("status", "PROCESSING").containsEntry("razorpay_refund_id", null);
        assertThat(ticketStatus(s)).isEqualTo("REFUNDED");

        rzp.clearFailures();
        job.reconcileUnanswered(Duration.ZERO);

        assertThat(refund(s).get("razorpay_refund_id")).asString().startsWith("rfnd_fake");
        assertThat(refund(s)).containsEntry("status", "PROCESSING");
        assertThat(refundPosts(s.paymentId())).isEqualTo(1);
    }

    @Test
    void refundRazorpayNeverReceivedIsSentAgain() throws Exception {
        Sold s = sold(10, 1, 48);
        rzp.failPath("/v1/payments/" + s.paymentId() + "/refund", 503);
        assertThat(refundAll(s)).isEqualTo(200);
        assertThat(refund(s)).containsEntry("status", "PROCESSING");

        rzp.clearFailures();
        job.reconcileUnanswered(Duration.ZERO);

        assertThat(refund(s).get("razorpay_refund_id")).asString().startsWith("rfnd_fake");
        assertThat(jdbc.queryForObject("select attempts from refunds where booking_id = ?", Integer.class, s.booking().id()))
                .isEqualTo(2);
    }

    @Test
    void webhookFindsRefundByOurIdWhenTheReplyWasLost() throws Exception {
        Sold s = sold(10, 1, 48);
        rzp.loseRefundReplies(s.paymentId());
        refundAll(s);
        String ours = jdbc.queryForObject("select id::text from refunds where booking_id = ?", String.class, s.booking().id());
        String body = """
                {"entity":"event","event":"refund.processed","payload":{"refund":{"entity":{"id":"rfnd_lost1",
                "status":"processed","notes":{"refundId":"%s"}}}}}
                """.formatted(ours);

        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", SharedRazorpay.webhookSignature(body))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()).content(body))
                .andExpect(status().isOk());

        assertThat(refund(s)).containsEntry("status", "PROCESSED").containsEntry("razorpay_refund_id", "rfnd_lost1");
    }

    @Test
    void refusedCancellationRefundKeepsTicketsRefundedAndIsRetried() throws Exception {
        Sold s = sold(10, 2, null);
        rzp.failPath("/v1/payments/" + s.paymentId() + "/refund", 400);

        mvc.perform(post("/api/v1/organizer/events/{id}/cancel", s.event().eventId())
                        .header("Authorization", s.event().organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Artist unwell\"}"))
                .andExpect(status().isOk());

        assertThat(refund(s)).containsEntry("status", "FAILED").containsEntry("needs_retry", true)
                .containsEntry("kind", "CANCELLATION");
        assertThat(ticketStatus(s)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, s.booking().id()))
                .isEqualTo("REFUNDED");

        rzp.clearFailures();
        job.retryRefused(Duration.ZERO);

        assertThat(refund(s)).containsEntry("status", "PROCESSING").containsEntry("needs_retry", false);
    }

    @Test
    void refusedRefundWhoseSeatsWereResoldIsRetriedNotRevived() throws Exception {
        Sold s = sold(2, 2, 48);
        assertThat(refundAll(s)).isEqualTo(200);
        BookingResponse other = support.confirmed(s.event(), 2, users.attendee());
        assertThat(other.status()).isEqualTo("CONFIRMED");
        String rfnd = (String) refund(s).get("razorpay_refund_id");
        String body = """
                {"entity":"event","event":"refund.failed","payload":{"refund":{"entity":{"id":"%s","status":"failed"}}}}
                """.formatted(rfnd);

        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", SharedRazorpay.webhookSignature(body))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()).content(body))
                .andExpect(status().isOk());

        assertThat(refund(s)).containsEntry("status", "FAILED").containsEntry("needs_retry", true);
        assertThat(ticketStatus(s)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForMap("select total, sold, held from ticket_inventory where ticket_type_id = ?",
                s.event().ticketTypeId())).containsEntry("sold", 2).containsEntry("total", 2);
    }

    @Test
    void sweepRefundsCancelledEventsTheHookMissed() {
        Sold s = sold(10, 1, null);
        BookingResponse pending = support.book(s.event(), 1, users.attendee());
        jdbc.update("update events set status = 'CANCELLED' where id = ?", s.event().eventId());

        assertThat(job.sweepCancelledEvents()).isGreaterThanOrEqualTo(1);

        assertThat(refund(s)).containsEntry("kind", "CANCELLATION").containsEntry("status", "PROCESSING");
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, pending.id()))
                .isEqualTo("EXPIRED");
        job.sweepCancelledEvents();
        assertThat(jdbc.queryForObject("select count(*) from refunds where booking_id = ?", Integer.class, s.booking().id()))
                .isEqualTo(1);
    }

    @Test
    void paymentForACancelledEventIsRefundedNotConfirmed() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.book(e, 2, users.attendee());
        jdbc.update("update events set status = 'CANCELLED' where id = ?", e.eventId());
        String pay = BookingTestSupport.capturedPayment(b);

        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.LATE_PAYMENT);

        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForMap("select kind, amount_paise from refunds where booking_id = ?", b.id()))
                .containsEntry("kind", "LATE_PAYMENT").containsEntry("amount_paise", b.checkout().amountPaise());
        assertThat(jdbc.queryForObject("select held + sold from ticket_inventory where ticket_type_id = ?", Integer.class,
                e.ticketTypeId())).isZero();
    }

    @Test
    void lateMoneyForAReplacedCheckoutIsRefunded() {
        LiveEvent e = support.liveEvent(49900, 10);
        TestUser buyer = users.attendee();
        BookingResponse first = support.book(e, 2, buyer);
        support.book(e, 1, buyer); // replaces the first checkout
        String pay = BookingTestSupport.capturedPayment(first);

        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.LATE_PAYMENT);

        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, first.id())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from refunds where booking_id = ?", Integer.class, first.id())).isEqualTo(1);
    }

    @Test
    void lateMoneyAfterTheEventStartedIsRefunded() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.book(e, 1, users.attendee());
        new TransactionTemplate(txManager).executeWithoutResult(s -> expiry.expire(b.id(), "HOLD_EXPIRED"));
        jdbc.update("update events set starts_at = now() - interval '10 minutes' where id = ?", e.eventId());
        String pay = BookingTestSupport.capturedPayment(b);

        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.LATE_PAYMENT);
        assertThat(jdbc.queryForObject("select count(*) from refunds where booking_id = ?", Integer.class, b.id())).isEqualTo(1);
    }
}
