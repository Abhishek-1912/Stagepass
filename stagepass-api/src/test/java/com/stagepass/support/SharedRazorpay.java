package com.stagepass.support;

/** One fake Razorpay for the whole test JVM; the Spring context's razorpay.base-url points at it. */
public final class SharedRazorpay {

    private static final FakeRazorpayServer SERVER = new FakeRazorpayServer();

    private SharedRazorpay() {
    }

    public static FakeRazorpayServer server() {
        return SERVER;
    }

    /** Signature the browser would receive from Razorpay Checkout (test key secret). */
    public static String checkoutSignature(String orderId, String paymentId) {
        return FakeRazorpayServer.hmac(orderId + "|" + paymentId, "test_secret");
    }

    public static String webhookSignature(String body) {
        return FakeRazorpayServer.hmac(body, "whsec_test");
    }
}
