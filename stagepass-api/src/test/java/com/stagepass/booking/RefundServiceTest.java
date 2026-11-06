package com.stagepass.booking;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundServiceTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private record Sold(LiveEvent event, TestUser buyer, BookingResponse booking) {
    }

    /** 2 × ₹499 confirmed booking (total 105104) on an event 10 days out with the given refund policy. */
    private Sold sold(long price, Integer cutoffHours) {
        LiveEvent e = support.liveEvent(price, 20);
        if (cutoffHours != null) {
            jdbc.update("update events set refund_policy = 'UNTIL_HOURS_BEFORE', refund_cutoff_hours = ? where id = ?",
                    cutoffHours, e.eventId());
        }
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(e, 2, buyer);
        assertThat(b.status()).isEqualTo("CONFIRMED");
        return new Sold(e, buyer, b);
    }

    private ResultActions requestRefund(Sold s, String body) throws Exception {
        return mvc.perform(post("/api/v1/me/bookings/{id}/refunds", s.booking().id()).header("Authorization", s.buyer().bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String tickets(UUID... ids) {
        StringBuilder sb = new StringBuilder("{\"ticketIds\":[");
        for (int i = 0; i < ids.length; i++) {
            sb.append(i > 0 ? "," : "").append('"').append(ids[i]).append('"');
        }
        return sb.append("],\"reason\":\"Can't make it\"}").toString();
    }

    private String ticketStatus(UUID id) {
        return jdbc.queryForObject("select status from tickets where id = ?", String.class, id);
    }

    private String bookingStatus(Sold s) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, s.booking().id());
    }

    private int sold(LiveEvent e) {
        return jdbc.queryForObject("select sold from ticket_inventory where ticket_type_id = ?", Integer.class, e.ticketTypeId());
    }

    private long refundCalls() {
        return SharedRazorpay.server().count(r -> r.path().endsWith("/refund"));
    }

    private String refundStatus(Sold s) {
        return jdbc.queryForObject("select status from refunds where booking_id = ? order by created_at desc limit 1",
                String.class, s.booking().id());
    }

    private void webhook(String body) throws Exception {
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", SharedRazorpay.webhookSignature(body))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()).content(body))
                .andExpect(status().isOk());
    }

    private String razorpayRefundId(Sold s) {
        return jdbc.queryForObject("select razorpay_refund_id from refunds where booking_id = ?", String.class, s.booking().id());
    }

    @Test
    void attendeeRefundInsidePolicyKeepsFee() throws Exception {
        Sold s = sold(49900, 48);
        UUID first = s.booking().tickets().get(0).id();
        long calls = refundCalls();

        requestRefund(s, tickets(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refund.history[0].amountPaise").value(49900))
                .andExpect(jsonPath("$.refund.history[0].status").value("PROCESSING"));

        assertThat(ticketStatus(first)).isEqualTo("REFUNDED");
        assertThat(ticketStatus(s.booking().tickets().get(1).id())).isEqualTo("VALID");
        assertThat(sold(s.event())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount_paise from organizer_ledger_entries where booking_id = ? and type = 'REFUND'",
                Long.class, s.booking().id())).isEqualTo(-49900L);
        assertThat(refundCalls()).isEqualTo(calls + 1);
        var received = SharedRazorpay.server().received();
        assertThat(received.get(received.size() - 1).body()).contains("\"amount\":49900");
    }

    @Test
    void rescheduleWindowRefundsFeeToo() throws Exception {
        Sold s = sold(49900, null);
        jdbc.update("update events set reschedule_refund_until = now() + interval '3 days' where id = ?", s.event().eventId());

        requestRefund(s, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refund.history[0].kind").value("RESCHEDULE"))
                .andExpect(jsonPath("$.refund.history[0].amountPaise").value(105104));
        assertThat(sold(s.event())).isZero();
    }

    @Test
    void outsidePolicyNeedsOrganizerApproval() throws Exception {
        Sold s = sold(49900, null);
        long calls = refundCalls();

        requestRefund(s, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.refund.history[0].status").value("PENDING_APPROVAL"));
        assertThat(refundCalls()).isEqualTo(calls);
        requestRefund(s, "{}").andExpect(status().isConflict());

        String list = mvc.perform(get("/api/v1/organizer/events/{id}/refund-requests", s.event().eventId())
                        .header("Authorization", s.event().organizer().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$[0].amountPaise").value(99800))
                .andReturn().getResponse().getContentAsString();
        String refundId = JsonPath.read(list, "$[0].id");

        mvc.perform(post("/api/v1/organizer/refund-requests/{id}/approve", refundId)
                        .header("Authorization", s.event().organizer().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        assertThat(bookingStatus(s)).isEqualTo("REFUNDED");
        assertThat(refundCalls()).isEqualTo(calls + 1);

        mvc.perform(post("/api/v1/organizer/refund-requests/{id}/decline", refundId)
                        .header("Authorization", s.event().organizer().bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void organizerCanDecline() throws Exception {
        Sold s = sold(49900, null);
        requestRefund(s, "{}").andExpect(status().isOk());
        String refundId = jdbc.queryForObject("select id::text from refunds where booking_id = ?", String.class, s.booking().id());

        mvc.perform(post("/api/v1/organizer/refund-requests/{id}/decline", refundId)
                        .header("Authorization", s.event().organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"No refunds within a week of the show\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.note").value("No refunds within a week of the show"));

        assertThat(bookingStatus(s)).isEqualTo("CONFIRMED");
        assertThat(ticketStatus(s.booking().tickets().get(0).id())).isEqualTo("VALID");
        // Declined tickets can be asked for again.
        requestRefund(s, "{}").andExpect(status().isOk());
    }

    @Test
    void otherOrganizerCannotDecide() throws Exception {
        Sold s = sold(49900, null);
        requestRefund(s, "{}").andExpect(status().isOk());
        String refundId = jdbc.queryForObject("select id::text from refunds where booking_id = ?", String.class, s.booking().id());
        TestUser other = users.approvedOrganizer();

        mvc.perform(post("/api/v1/organizer/refund-requests/{id}/approve", refundId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organizer/events/{id}/refund-requests", s.event().eventId()).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        assertThat(refundStatus(s)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void refundWebhookMarksProcessed() throws Exception {
        Sold s = sold(49900, 48);
        requestRefund(s, "{}").andExpect(status().isOk());

        webhook("""
                {"entity":"event","event":"refund.processed","payload":{"refund":{"entity":{"id":"%s","status":"processed"}}}}
                """.formatted(razorpayRefundId(s)));

        assertThat(refundStatus(s)).isEqualTo("PROCESSED");
    }

    @Test
    void failedRefundRestoresTickets() throws Exception {
        Sold s = sold(49900, 48);
        requestRefund(s, "{}").andExpect(status().isOk());
        assertThat(bookingStatus(s)).isEqualTo("REFUNDED");

        webhook("""
                {"entity":"event","event":"refund.failed","payload":{"refund":{"entity":{"id":"%s","status":"failed"}}}}
                """.formatted(razorpayRefundId(s)));

        assertThat(refundStatus(s)).isEqualTo("FAILED");
        assertThat(bookingStatus(s)).isEqualTo("CONFIRMED");
        assertThat(ticketStatus(s.booking().tickets().get(0).id())).isEqualTo("VALID");
        assertThat(sold(s.event())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select sum(amount_paise) from organizer_ledger_entries where booking_id = ?",
                Long.class, s.booking().id())).isEqualTo(99800L);
    }

    @Test
    void gatewayRejectionIs503AndChangesNothing() throws Exception {
        Sold s = sold(49900, 48);
        String pay = jdbc.queryForObject("select razorpay_payment_id from payments where booking_id = ?", String.class,
                s.booking().id());
        SharedRazorpay.server().failPath("/v1/payments/" + pay + "/refund", 400);
        try {
            requestRefund(s, "{}").andExpect(status().isServiceUnavailable());
        } finally {
            SharedRazorpay.server().clearFailures();
        }
        assertThat(bookingStatus(s)).isEqualTo("CONFIRMED");
        assertThat(refundStatus(s)).isEqualTo("FAILED");
        assertThat(sold(s.event())).isEqualTo(2);
        // A later attempt goes through.
        requestRefund(s, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void checkedInTicketCannotBeRefunded() throws Exception {
        Sold s = sold(49900, 48);
        UUID used = s.booking().tickets().get(0).id();
        jdbc.update("update tickets set status = 'CHECKED_IN' where id = ?", used);

        requestRefund(s, tickets(used)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ticketIds[0]"));
        requestRefund(s, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
        assertThat(ticketStatus(used)).isEqualTo("CHECKED_IN");
    }

    @Test
    void freeBookingRefundSkipsRazorpay() throws Exception {
        Sold s = sold(0, 48);
        long calls = refundCalls();

        requestRefund(s, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refund.history[0].status").value("PROCESSED"));

        assertThat(refundCalls()).isEqualTo(calls);
        assertThat(sold(s.event())).isZero();
    }

    @Test
    void refundsCloseWhenTheEventStarts() throws Exception {
        Sold s = sold(49900, 48);
        jdbc.update("update events set starts_at = now() - interval '1 hour' where id = ?", s.event().eventId());
        requestRefund(s, "{}").andExpect(status().isConflict());
        assertThat(bookingStatus(s)).isEqualTo("CONFIRMED");
    }
}
