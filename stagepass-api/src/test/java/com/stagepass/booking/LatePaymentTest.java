package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.BookingExpiryService;
import com.stagepass.booking.service.LatePaymentHandler;
import com.stagepass.payment.service.PaymentService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class LatePaymentTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    PaymentService payments;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    private void expire(BookingResponse b) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> expiry.expire(b.id(), "HOLD_EXPIRED"));
    }

    @Test
    void latePaymentWithStockStillConfirms() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.book(e, 2, users.attendee());
        expire(b);
        String pay = BookingTestSupport.capturedPayment(b);

        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.CONFIRMED);

        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForMap("select sold, held from ticket_inventory where ticket_type_id = ?", e.ticketTypeId()))
                .containsEntry("sold", 2).containsEntry("held", 0);
        assertThat(jdbc.queryForObject("select count(*) from tickets where booking_id = ?", Integer.class, b.id())).isEqualTo(2);
    }

    @Test
    void latePaymentAfterSellOutIsRefunded() {
        LiveEvent e = support.liveEvent(49900, 2);
        BookingResponse late = support.book(e, 2, users.attendee());
        expire(late);
        support.book(e, 2, users.attendee());
        String pay = BookingTestSupport.capturedPayment(late);

        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.LATE_PAYMENT);

        assertThat(jdbc.queryForMap("select status, cancel_reason from bookings where id = ?", late.id()))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", LatePaymentHandler.SOLD_OUT_REASON);
        assertThat(jdbc.queryForMap("select kind, status, amount_paise, razorpay_refund_id from refunds where booking_id = ?", late.id()))
                .containsEntry("kind", "LATE_PAYMENT").containsEntry("status", "PROCESSING")
                .containsEntry("amount_paise", late.checkout().amountPaise());
        assertThat(SharedRazorpay.server().count(r -> r.path().equals("/v1/payments/" + pay + "/refund"))).isEqualTo(1);
        assertThat(jdbc.queryForMap("select sold, held from ticket_inventory where ticket_type_id = ?", e.ticketTypeId()))
                .containsEntry("sold", 0).containsEntry("held", 2);

        // The webhook for the same payment arrives afterwards: no second refund.
        assertThat(payments.settle(pay).outcome()).isEqualTo(PaymentService.Outcome.LATE_PAYMENT);
        assertThat(SharedRazorpay.server().count(r -> r.path().equals("/v1/payments/" + pay + "/refund"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refunds where booking_id = ?", Integer.class, late.id())).isEqualTo(1);
    }

    @Test
    void refundProcessedWebhookCompletesTheRefund() throws Exception {
        LiveEvent e = support.liveEvent(49900, 1);
        BookingResponse late = support.book(e, 1, users.attendee());
        expire(late);
        support.book(e, 1, users.attendee());
        String pay = BookingTestSupport.capturedPayment(late);
        payments.settle(pay);
        String refundId = jdbc.queryForObject("select razorpay_refund_id from refunds where booking_id = ?", String.class, late.id());
        String body = """
                {"entity":"event","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","payment_id":"%s","status":"processed"}}}}
                """.formatted(refundId, pay);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/webhooks/razorpay")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", SharedRazorpay.webhookSignature(body))
                        .header("X-Razorpay-Event-Id", "evt_" + refundId).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(jdbc.queryForObject("select status from refunds where booking_id = ?", String.class, late.id()))
                .isEqualTo("PROCESSED");
    }
}
