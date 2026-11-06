package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.payment.service.PaymentService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfirmationTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PaymentService payments;

    private record Pending(TestUser buyer, LiveEvent event, BookingResponse booking) {
    }

    private Pending pending(int qty) {
        LiveEvent e = support.liveEvent(49900, 50);
        TestUser buyer = users.attendee();
        return new Pending(buyer, e, support.book(e, qty, buyer));
    }

    private ResultActions verify(TestUser buyer, BookingResponse b, String paymentId, String signature) throws Exception {
        String body = """
                {"bookingId":"%s","razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}
                """.formatted(b.id(), b.checkout().razorpayOrderId(), paymentId, signature);
        return mvc.perform(post("/api/v1/payments/verify").header("Authorization", buyer.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String sign(BookingResponse b, String paymentId) {
        return SharedRazorpay.checkoutSignature(b.checkout().razorpayOrderId(), paymentId);
    }

    private String bookingStatus(BookingResponse b) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, b.id());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void validPaymentConfirmsAndIssuesTickets() throws Exception {
        Pending p = pending(3);
        String pay = BookingTestSupport.capturedPayment(p.booking());

        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.checkout").doesNotExist())
                .andExpect(jsonPath("$.tickets", hasSize(3)))
                .andExpect(jsonPath("$.invoiceNumber").exists());

        assertThat(jdbc.queryForMap("select sold, held from ticket_inventory where ticket_type_id = ?",
                p.event().ticketTypeId())).containsEntry("sold", 3).containsEntry("held", 0);
        assertThat(jdbc.queryForList("select type, amount_paise from organizer_ledger_entries where booking_id = ?",
                p.booking().id())).containsExactly(Map.of("type", "SALE", "amount_paise", 149700L));
        assertThat(jdbc.queryForMap("select status, razorpay_payment_id, method, gateway_fee_paise from payments where booking_id = ?",
                p.booking().id())).containsEntry("status", "CAPTURED").containsEntry("razorpay_payment_id", pay)
                .containsEntry("method", "upi").containsEntry("gateway_fee_paise", 2480L);
        assertThat(count("select count(*) from audit_logs where action = 'BOOKING_CONFIRMED' and entity_id = ?",
                p.booking().id().toString())).isEqualTo(1);
    }

    @Test
    void forgedSignatureChangesNothing() throws Exception {
        Pending p = pending(2);
        String pay = BookingTestSupport.capturedPayment(p.booking());

        verify(p.buyer(), p.booking(), pay, "0".repeat(64)).andExpect(status().isBadRequest());

        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
        assertThat(count("select count(*) from audit_logs where action = 'PAYMENT_MISMATCH' and entity_id = ?",
                p.booking().id().toString())).isEqualTo(1);
        assertThat(count("select count(*) from tickets where booking_id = ?", p.booking().id())).isZero();
    }

    @Test
    void amountMismatchIsNotConfirmed() throws Exception {
        Pending p = pending(2);
        String pay = BookingTestSupport.scriptedPayment(p.booking(), 100, "captured");

        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay)).andExpect(status().isBadRequest());

        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select status from payments where booking_id = ?", String.class,
                p.booking().id())).isEqualTo("FAILED");
    }

    @Test
    void otherUsersBookingIs404() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        verify(users.attendee(), p.booking(), pay, sign(p.booking(), pay)).andExpect(status().isNotFound());
        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void authorizedPaymentIsCaptured() throws Exception {
        Pending p = pending(1);
        String pay = BookingTestSupport.scriptedPayment(p.booking(), p.booking().checkout().amountPaise(), "authorized");

        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(SharedRazorpay.server().count(r -> r.path().equals("/v1/payments/" + pay + "/capture"))).isEqualTo(1);
    }

    @Test
    void verifyTwiceConfirmsOnce() throws Exception {
        Pending p = pending(3);
        String pay = BookingTestSupport.capturedPayment(p.booking());

        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay)).andExpect(status().isOk());
        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tickets", hasSize(3)));

        assertThat(count("select count(*) from tickets where booking_id = ?", p.booking().id())).isEqualTo(3);
        assertThat(count("select count(*) from organizer_ledger_entries where booking_id = ? and type = 'SALE'",
                p.booking().id())).isEqualTo(1);
    }

    @Test
    void concurrentConfirmationsConvergeToOne() throws Exception {
        Pending p = pending(3);
        String pay = BookingTestSupport.capturedPayment(p.booking());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<PaymentService.SettleResult>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return payments.settle(pay);
            }));
        }
        start.countDown();
        List<PaymentService.Outcome> outcomes = new ArrayList<>();
        for (Future<PaymentService.SettleResult> f : results) {
            outcomes.add(f.get().outcome());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o == PaymentService.Outcome.CONFIRMED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o == PaymentService.Outcome.ALREADY_CONFIRMED).hasSize(5);
        assertThat(count("select count(*) from tickets where booking_id = ?", p.booking().id())).isEqualTo(3);
        assertThat(count("select count(*) from organizer_ledger_entries where booking_id = ? and type = 'SALE'",
                p.booking().id())).isEqualTo(1);
        assertThat(count("select count(*) from invoices where booking_id = ?", p.booking().id())).isEqualTo(1);
    }

    @Test
    void unknownPaymentIdIs400() throws Exception {
        Pending p = pending(1);
        String pay = "pay_doesnotexist" + UUID.randomUUID().toString().substring(0, 4);
        verify(p.buyer(), p.booking(), pay, sign(p.booking(), pay)).andExpect(status().isBadRequest());
        assertThat(bookingStatus(p.booking())).isEqualTo("PENDING_PAYMENT");
    }
}
