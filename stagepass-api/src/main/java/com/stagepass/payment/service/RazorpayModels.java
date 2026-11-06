package com.stagepass.payment.service;

/** Minimal views of Razorpay API objects. Amounts are paise. */
public final class RazorpayModels {

    private RazorpayModels() {
    }

    public record Order(String id, long amount, String status) {
    }

    public record Payment(String id, String orderId, long amount, String currency, String status, String method,
            Long fee, Long tax, String errorDescription, String rawJson) {
    }

    public record Refund(String id, String paymentId, long amount, String status) {
    }
}
