package com.stagepass.admin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRefundTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions refund(TestUser admin, UUID bookingId, String ticketIds, boolean includeFee) throws Exception {
        return mvc.perform(post("/api/v1/admin/bookings/" + bookingId + "/refund").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketIds\":%s,\"includeFee\":%s,\"reason\":\"Customer complaint\"}".formatted(ticketIds, includeFee)));
    }

    @Test
    void adminRefundsWholeBookingIgnoringPolicy() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        jdbc.update("update events set refund_policy = 'NONE' where id = ?", e.eventId());
        BookingResponse b = support.confirmed(e, 2, users.attendee());

        refund(admin, b.id(), "[]", true).andExpect(status().isOk());

        Map<String, Object> r = jdbc.queryForMap("select kind, amount_paise, fee_paise, status, decided_by from refunds"
                + " where booking_id = ?", b.id());
        assertThat(r).containsEntry("kind", "ADMIN").containsEntry("amount_paise", b.price().totalPaise())
                .containsEntry("status", "PROCESSING").containsEntry("decided_by", admin.id());
        assertThat((Long) r.get("fee_paise")).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from tickets where booking_id = ? and status = 'REFUNDED'",
                Integer.class, b.id())).isEqualTo(2);
        String after = jdbc.queryForObject("select after::text from audit_logs where action = 'REFUND_ISSUED' and entity_id = ?",
                String.class, b.id().toString());
        assertThat(after).contains("\"byAdmin\": true");
    }

    @Test
    void withoutFeeOnlyTicketPriceIsReturned() throws Exception {
        TestUser admin = users.admin();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 20), 1, users.attendee());
        refund(admin, b.id(), "[]", false).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select amount_paise from refunds where booking_id = ?", Long.class, b.id()))
                .isEqualTo(49900L);
    }

    @Test
    void adminRefundSkipsNothingSilently() throws Exception {
        TestUser admin = users.admin();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 20), 2, users.attendee());
        UUID used = b.tickets().get(0).id();
        UUID live = b.tickets().get(1).id();
        jdbc.update("update tickets set status = 'CHECKED_IN' where id = ?", used);

        refund(admin, b.id(), "[\"" + used + "\"]", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ticketIds[0]"))
                .andExpect(jsonPath("$.errors[0].message").value("was already used to enter the event"));
        refund(admin, b.id(), "[]", true).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from tickets where id = ?", String.class, live)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select status from tickets where id = ?", String.class, used)).isEqualTo("CHECKED_IN");
        refund(admin, b.id(), "[]", true).andExpect(status().isConflict());
    }

    @Test
    void foreignTicketIdIs400() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        BookingResponse other = support.confirmed(e, 1, users.attendee());
        refund(admin, b.id(), "[\"" + other.tickets().get(0).id() + "\"]", true)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("ticketIds[0]"));
        refund(admin, UUID.randomUUID(), "[]", true).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/bookings/" + b.id() + "/refund").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"includeFee\":true,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryOnlyForFailedNeedingRetry() throws Exception {
        TestUser admin = users.admin();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 20), 1, users.attendee());
        refund(admin, b.id(), "[]", true).andExpect(status().isOk());
        UUID refundId = jdbc.queryForObject("select id from refunds where booking_id = ?", UUID.class, b.id());

        mvc.perform(post("/api/v1/admin/refunds/" + refundId + "/retry").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
        jdbc.update("update refunds set status = 'FAILED', needs_retry = true, razorpay_refund_id = null where id = ?", refundId);
        mvc.perform(post("/api/v1/admin/refunds/" + refundId + "/retry").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REFUND_RETRIED' and entity_id = ?",
                Integer.class, refundId.toString())).isEqualTo(1);
        mvc.perform(post("/api/v1/admin/refunds/" + UUID.randomUUID() + "/retry").header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
    }
}
