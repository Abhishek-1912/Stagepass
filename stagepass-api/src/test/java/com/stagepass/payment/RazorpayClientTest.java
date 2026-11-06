package com.stagepass.payment;

import com.stagepass.payment.config.RazorpayProperties;
import com.stagepass.payment.service.RazorpayClient;
import com.stagepass.payment.service.RazorpayException;
import com.stagepass.payment.service.RazorpaySignatures;
import com.stagepass.support.FakeRazorpayServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RazorpayClientTest {

    FakeRazorpayServer fake;
    RazorpayClient client;
    RazorpaySignatures signatures;

    @BeforeEach
    void setUp() {
        fake = new FakeRazorpayServer();
        RazorpayProperties props = new RazorpayProperties("rzp_test_key", "test_secret", "whsec_test", fake.baseUrl());
        client = new RazorpayClient(props);
        signatures = new RazorpaySignatures(props);
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    @Test
    void checkoutSignatureMatchesRazorpayFormula() {
        String good = FakeRazorpayServer.hmac("order_X|pay_Y", "test_secret");
        assertThat(signatures.checkout("order_X", "pay_Y", good)).isTrue();
        assertThat(signatures.checkout("order_X", "pay_Z", good)).isFalse();
        assertThat(signatures.checkout("order_X", "pay_Y", "")).isFalse();
        assertThat(signatures.checkout("order_X", "pay_Y", null)).isFalse();
    }

    @Test
    void webhookSignatureUsesRawBody() {
        String body = "{\"event\":\"payment.captured\"}";
        String sig = FakeRazorpayServer.hmac(body, "whsec_test");
        assertThat(signatures.webhook(body.getBytes(StandardCharsets.UTF_8), sig)).isTrue();
        assertThat(signatures.webhook("{\"event\": \"payment.captured\"}".getBytes(StandardCharsets.UTF_8), sig)).isFalse();
        assertThat(signatures.webhook(body.getBytes(StandardCharsets.UTF_8), null)).isFalse();
    }

    @Test
    void createsOrderWithBasicAuthAndPaise() {
        var order = client.createOrder(105104, "SP-ABC123", Map.of("bookingId", "b1"));

        assertThat(order.id()).startsWith("order_");
        assertThat(order.amount()).isEqualTo(105104);
        FakeRazorpayServer.Received req = fake.received().get(0);
        assertThat(req.path()).isEqualTo("/v1/orders");
        assertThat(req.authorization()).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString("rzp_test_key:test_secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(req.body()).contains("\"amount\":105104").contains("\"currency\":\"INR\"").contains("\"receipt\":\"SP-ABC123\"");
    }

    @Test
    void fetchesPaymentFields() {
        fake.payment("pay_1", "order_9", 105104, "captured");
        var p = client.fetchPayment("pay_1");
        assertThat(p.id()).isEqualTo("pay_1");
        assertThat(p.orderId()).isEqualTo("order_9");
        assertThat(p.amount()).isEqualTo(105104);
        assertThat(p.status()).isEqualTo("captured");
        assertThat(p.fee()).isEqualTo(2480);
        assertThat(p.tax()).isEqualTo(378);
    }

    @Test
    void capturesAuthorizedPayment() {
        fake.payment("pay_2", "order_9", 500, "authorized");
        assertThat(client.capture("pay_2", 500).status()).isEqualTo("captured");
    }

    @Test
    void refundsWithNormalSpeed() {
        var r = client.refund("pay_3", 49900, "SP-ABC123-R1", Map.of("kind", "ATTENDEE_REQUEST"));
        assertThat(r.id()).startsWith("rfnd_");
        assertThat(fake.received().get(0).body()).contains("\"amount\":49900").contains("\"speed\":\"normal\"");
    }

    @Test
    void serverErrorsAreRetryableClientErrorsAreNot() {
        fake.failPath("/v1/orders", 500);
        assertThatThrownBy(() -> client.createOrder(100, "r", Map.of()))
                .isInstanceOfSatisfying(RazorpayException.class, e -> assertThat(e.retryable()).isTrue());
        fake.clearFailures();
        assertThatThrownBy(() -> client.fetchPayment("pay_unknown"))
                .isInstanceOfSatisfying(RazorpayException.class, e -> assertThat(e.retryable()).isFalse());
    }

    @Test
    void unconfiguredClientRefuses() {
        RazorpayClient none = new RazorpayClient(new RazorpayProperties("", "", "", fake.baseUrl()));
        assertThatThrownBy(() -> none.createOrder(100, "r", Map.of())).isInstanceOf(RazorpayException.class);
    }
}
