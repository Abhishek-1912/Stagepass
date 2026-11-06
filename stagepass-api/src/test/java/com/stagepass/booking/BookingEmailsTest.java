package com.stagepass.booking;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.BookingExpiryService;
import com.stagepass.booking.service.RefundMaintenanceJob;
import com.stagepass.notification.config.ResendProperties;
import com.stagepass.notification.service.EmailAttachmentProvider;
import com.stagepass.notification.service.EmailDispatcher;
import com.stagepass.notification.service.EmailRenderer;
import com.stagepass.notification.service.ResendClient;
import com.stagepass.payment.service.PaymentService;
import com.stagepass.support.FakeResendServer;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Which emails the booking lifecycle queues, and that each business fact queues exactly one. */
class BookingEmailsTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PaymentService payments;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    RefundMaintenanceJob refundJob;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    EmailRenderer renderer;

    @Autowired
    List<EmailAttachmentProvider> providers;

    private int emails(String template, UUID bookingId) {
        return jdbc.queryForObject("select count(*) from email_outbox where template = ? and payload->>'bookingId' = ?",
                Integer.class, template, bookingId.toString());
    }

    private Map<String, Object> payload(String template, UUID bookingId) {
        String json = jdbc.queryForObject("select payload::text from email_outbox where template = ? and payload->>'bookingId' = ?",
                String.class, template, bookingId.toString());
        return JsonPath.read(json, "$");
    }

    private void expire(UUID bookingId, String reason) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> expiry.expire(bookingId, reason));
    }

    private void webhook(String body) throws Exception {
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", SharedRazorpay.webhookSignature(body))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void verifyWebhookAndRetrySendOneConfirmation() {
        BookingResponse b = support.book(support.liveEvent(49900, 10), 2, users.attendee());
        String pay = BookingTestSupport.capturedPayment(b);

        payments.settle(pay);
        payments.settle(pay);
        payments.settle(pay);

        assertThat(emails("BOOKING_CONFIRMED", b.id())).isEqualTo(1);
        Map<String, Object> p = payload("BOOKING_CONFIRMED", b.id());
        assertThat(p).containsEntry("bookingCode", b.code()).containsEntry("totalPaise", 105104);
        assertThat((List<?>) p.get("tickets")).hasSize(2);
        assertThat(jdbc.queryForObject("select to_address from email_outbox where template = 'BOOKING_CONFIRMED' "
                + "and payload->>'bookingId' = ?", String.class, b.id().toString())).isEqualTo("asha@test.in");
    }

    @Test
    void freeBookingGetsConfirmation() {
        BookingResponse b = support.book(support.liveEvent(0, 10), 1, users.attendee());
        assertThat(emails("BOOKING_CONFIRMED", b.id())).isEqualTo(1);
    }

    @Test
    void onlyRealHoldExpiryEmails() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse timedOut = support.book(e, 1, users.attendee());
        BookingResponse gateway = support.book(e, 1, users.attendee());
        TestUser switcher = users.attendee();
        BookingResponse replaced = support.book(e, 1, switcher);
        support.book(e, 2, switcher);

        expire(timedOut.id(), "HOLD_EXPIRED");
        expire(gateway.id(), "PAYMENT_GATEWAY_UNAVAILABLE");

        assertThat(emails("BOOKING_EXPIRED", timedOut.id())).isEqualTo(1);
        assertThat(emails("BOOKING_EXPIRED", gateway.id())).isZero();
        assertThat(emails("BOOKING_EXPIRED", replaced.id())).isZero();
    }

    @Test
    void processedRefundEmailsOnce() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        jdbc.update("update events set refund_policy = 'UNTIL_HOURS_BEFORE', refund_cutoff_hours = 48 where id = ?", e.eventId());
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(e, 2, buyer);
        mvc.perform(post("/api/v1/me/bookings/{id}/refunds", b.id()).header("Authorization", buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketIds\":[\"" + b.tickets().get(0).id() + "\"]}"))
                .andExpect(status().isOk());
        String rfnd = jdbc.queryForObject("select razorpay_refund_id from refunds where booking_id = ?", String.class, b.id());
        String body = """
                {"entity":"event","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","status":"processed"}}}}
                """.formatted(rfnd);

        webhook(body);
        webhook(body);

        assertThat(emails("REFUND_PROCESSED", b.id())).isEqualTo(1);
        assertThat(payload("REFUND_PROCESSED", b.id())).containsEntry("amountPaise", 49900)
                .containsEntry("includesFee", false).containsEntry("ticketCount", 1);
    }

    @Test
    void declinedRequestEmails() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(e, 1, buyer);
        mvc.perform(post("/api/v1/me/bookings/{id}/refunds", b.id()).header("Authorization", buyer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        String refundId = jdbc.queryForObject("select id::text from refunds where booking_id = ?", String.class, b.id());

        mvc.perform(post("/api/v1/organizer/refund-requests/{id}/decline", refundId)
                        .header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Too close to the show\"}"))
                .andExpect(status().isOk());

        assertThat(emails("REFUND_DECLINED", b.id())).isEqualTo(1);
        assertThat(payload("REFUND_DECLINED", b.id())).containsEntry("note", "Too close to the show");
    }

    @Test
    void cancellingEventEmailsEachBookingOnce() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse a = support.confirmed(e, 2, users.attendee());
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        BookingResponse pending = support.book(e, 1, users.attendee());

        mvc.perform(post("/api/v1/organizer/events/{id}/cancel", e.eventId()).header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Venue flooded\"}"))
                .andExpect(status().isOk());

        assertThat(emails("EVENT_CANCELLED", a.id())).isEqualTo(1);
        assertThat(emails("EVENT_CANCELLED", b.id())).isEqualTo(1);
        assertThat(emails("EVENT_CANCELLED", pending.id())).isEqualTo(1);
        assertThat(payload("EVENT_CANCELLED", a.id())).containsEntry("amountPaise", (int) a.price().totalPaise());
        assertThat(payload("EVENT_CANCELLED", pending.id())).containsEntry("amountPaise", 0);

        for (BookingResponse r : List.of(a, b)) {
            String rfnd = jdbc.queryForObject("select razorpay_refund_id from refunds where booking_id = ?", String.class, r.id());
            webhook("""
                    {"entity":"event","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","status":"processed"}}}}
                    """.formatted(rfnd));
        }
        refundJob.sweepCancelledEvents();

        assertThat(emails("EVENT_CANCELLED", a.id())).isEqualTo(1);
        assertThat(emails("REFUND_PROCESSED", a.id())).isZero();
        assertThat(emails("REFUND_PROCESSED", b.id())).isZero();
    }

    @Test
    void dateChangeEmailsHolders() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse a = support.confirmed(e, 1, users.attendee());
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        OffsetDateTime newStart = OffsetDateTime.now().plusDays(20).withNano(0);

        mvc.perform(patch("/api/v1/organizer/events/{id}", e.eventId()).header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(newStart, newStart.plusHours(3))))
                .andExpect(status().isOk());

        assertThat(emails("EVENT_CHANGED", a.id())).isEqualTo(1);
        assertThat(emails("EVENT_CHANGED", b.id())).isEqualTo(1);
        OffsetDateTime until = OffsetDateTime.parse((String) payload("EVENT_CHANGED", a.id()).get("refundUntil"));
        assertThat(until).isBetween(OffsetDateTime.now().plusDays(7).minusMinutes(5), OffsetDateTime.now().plusDays(7).plusMinutes(5));

        OffsetDateTime later = newStart.plusDays(1);
        mvc.perform(patch("/api/v1/organizer/events/{id}", e.eventId()).header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(later, later.plusHours(3))))
                .andExpect(status().isOk());

        assertThat(emails("EVENT_CHANGED", a.id())).isEqualTo(2);
    }

    @Test
    void emailFailureNeverBreaksBooking() {
        try (FakeResendServer resend = new FakeResendServer()) {
            resend.respondWith(500);
            BookingResponse b = support.confirmed(support.liveEvent(0, 10), 1, users.attendee());
            UUID emailId = jdbc.queryForObject("select id from email_outbox where template = 'BOOKING_CONFIRMED' "
                    + "and payload->>'bookingId' = ?", UUID.class, b.id().toString());
            jdbc.update("update email_outbox set send_after = now() - interval '1 day' where id = ?", emailId);

            new EmailDispatcher(jdbc, txManager, new ResendClient(new ResendProperties("re_test_key",
                    "Stagepass <onboarding@resend.dev>", resend.baseUrl())), renderer, providers).dispatchBatch();

            assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id())).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForMap("select status, attempts from email_outbox where id = ?", emailId))
                    .containsEntry("status", "PENDING").containsEntry("attempts", 1);
            assertThat(resend.received().stream().anyMatch(r -> r.body().contains("stagepass-" + b.code() + ".pdf"))).isTrue();
        }
    }
}
