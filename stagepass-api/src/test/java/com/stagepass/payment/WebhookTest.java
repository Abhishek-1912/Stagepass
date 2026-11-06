package com.stagepass.payment;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private record Pending(TestUser buyer, BookingResponse booking) {
    }

    private Pending pending(int qty) {
        LiveEvent e = support.liveEvent(49900, 50);
        TestUser buyer = users.attendee();
        return new Pending(buyer, support.book(e, qty, buyer));
    }

    private ResultActions send(String body, String eventId) throws Exception {
        return send(body, eventId, SharedRazorpay.webhookSignature(body));
    }

    private ResultActions send(String body, String eventId, String signature) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", signature).header("X-Razorpay-Event-Id", eventId).content(body));
    }

    private static String paymentEvent(String event, BookingResponse b, String paymentId, String status) {
        return """
                {"entity":"event","event":"%s","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,
                "currency":"INR","status":"%s","error_description":"Card declined by bank"}}}}
                """.formatted(event, paymentId, b.checkout().razorpayOrderId(), b.checkout().amountPaise(), status);
    }

    private static String eventId() {
        return "evt_" + UUID.randomUUID();
    }

    private String bookingStatus(BookingResponse b) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void capturedWebhookConfirmsBooking() throws Exception {
        Pending p = pending(2);
        String pay = BookingTestSupport.capturedPayment(p.booking());

        send(paymentEvent("payment.captured", p.booking(), pay, "captured"), eventId()).andExpect(status().isOk());

        assertThat(bookingStatus(p.booking())).isEqualTo("CONFIRMED");
        assertThat(count("select count(*) from tickets where booking_id = ?", p.booking().id())).isEqualTo(2);
    }

    @Test
    void badSignatureIs400AndIgnored() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        String id = eventId();

        send(paymentEvent("payment.captured", p.booking(), pay, "captured"), id, "f".repeat(64))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .content(paymentEvent("payment.captured", p.booking(), pay, "captured")))
                .andExpect(status().isBadRequest());

        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
        assertThat(count("select count(*) from webhook_events where event_id = ?", id)).isZero();
    }

    @Test
    void duplicateEventIdIsProcessedOnce() throws Exception {
        Pending p = pending(2);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        String body = paymentEvent("payment.captured", p.booking(), pay, "captured");
        String id = eventId();

        send(body, id).andExpect(status().isOk());
        send(body, id).andExpect(status().isOk());

        assertThat(SharedRazorpay.server().count(r -> r.path().equals("/v1/payments/" + pay))).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where action = 'BOOKING_CONFIRMED' and entity_id = ?",
                p.booking().id().toString())).isEqualTo(1);
    }

    @Test
    void verifyThenWebhookThenRetryConfirmOnce() throws Exception {
        Pending p = pending(3);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        String verifyBody = """
                {"bookingId":"%s","razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}
                """.formatted(p.booking().id(), p.booking().checkout().razorpayOrderId(), pay,
                SharedRazorpay.checkoutSignature(p.booking().checkout().razorpayOrderId(), pay));
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", p.buyer().bearer())
                .contentType(MediaType.APPLICATION_JSON).content(verifyBody)).andExpect(status().isOk());

        String captured = paymentEvent("payment.captured", p.booking(), pay, "captured");
        String id = eventId();
        send(captured, id).andExpect(status().isOk());
        send(captured, id).andExpect(status().isOk());
        send(paymentEvent("order.paid", p.booking(), pay, "captured"), eventId()).andExpect(status().isOk());

        assertThat(count("select count(*) from tickets where booking_id = ?", p.booking().id())).isEqualTo(3);
        assertThat(count("select count(*) from organizer_ledger_entries where booking_id = ? and type = 'SALE'",
                p.booking().id())).isEqualTo(1);
        assertThat(count("select count(*) from invoices where booking_id = ?", p.booking().id())).isEqualTo(1);
    }

    @Test
    void failedPaymentKeepsBookingPending() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.scriptedPayment(p.booking(), p.booking().checkout().amountPaise(), "failed");

        send(paymentEvent("payment.failed", p.booking(), pay, "failed"), eventId()).andExpect(status().isOk());

        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForMap("select status, failure_reason from payments where booking_id = ?", p.booking().id()))
                .containsEntry("status", "FAILED").containsEntry("failure_reason", "Card declined by bank");
        assertThat(count("select held from ticket_inventory i join booking_items b on b.ticket_type_id = i.ticket_type_id"
                + " where b.booking_id = ?", p.booking().id())).isEqualTo(1);
    }

    @Test
    void disputeIsRecorded() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        send(paymentEvent("payment.captured", p.booking(), pay, "captured"), eventId()).andExpect(status().isOk());
        String disputeId = "disp_" + UUID.randomUUID().toString().substring(0, 8);
        String dispute = """
                {"entity":"event","event":"payment.dispute.created","payload":{"dispute":{"entity":{"id":"%s",
                "payment_id":"%s","amount":%d,"reason_code":"fraud","status":"open"}}}}
                """.formatted(disputeId, pay, p.booking().checkout().amountPaise());

        send(dispute, eventId()).andExpect(status().isOk());

        assertThat(jdbc.queryForMap("select status, reason, amount_paise from disputes where razorpay_dispute_id = ?", disputeId))
                .containsEntry("status", "open").containsEntry("reason", "fraud")
                .containsEntry("amount_paise", p.booking().checkout().amountPaise());
        assertThat(count("select count(*) from audit_logs where action = 'DISPUTE_RECORDED' and entity_id = ?",
                p.booking().id().toString())).isEqualTo(1);
    }

    @Test
    void processingErrorReturns500ForRetry() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        String body = paymentEvent("payment.captured", p.booking(), pay, "captured");
        String id = eventId();

        SharedRazorpay.server().failPath("/v1/payments/" + pay, 500);
        try {
            send(body, id).andExpect(status().isInternalServerError());
        } finally {
            SharedRazorpay.server().clearFailures();
        }
        assertThat(jdbc.queryForObject("select processed_at is null from webhook_events where event_id = ?",
                Boolean.class, id)).isTrue();
        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");

        send(body, id).andExpect(status().isOk());
        assertThat(bookingStatus(p.booking())).isEqualTo("CONFIRMED");
    }
}
