package com.stagepass.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Razorpay credentials (test mode for now), bound from {@code razorpay.*}. */
@ConfigurationProperties(prefix = "razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret,
        @DefaultValue("https://api.razorpay.com") String baseUrl) {

    public boolean configured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}
