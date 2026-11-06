package com.stagepass.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** What Razorpay Checkout hands the browser on success. */
public record VerifyPaymentRequest(
        @NotNull UUID bookingId,
        @NotBlank @Size(max = 64) String razorpayOrderId,
        @NotBlank @Size(max = 64) String razorpayPaymentId,
        @NotBlank @Size(max = 128) String razorpaySignature) {
}
