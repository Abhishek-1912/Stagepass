package com.stagepass.payment.service;

import com.stagepass.payment.config.RazorpayProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay signature checks, compared in constant time.
 * Checkout: HMAC_SHA256(order_id + "|" + payment_id, key_secret). Webhook: HMAC_SHA256(raw body, webhook_secret).
 */
@Component
public class RazorpaySignatures {

    private final RazorpayProperties props;

    public RazorpaySignatures(RazorpayProperties props) {
        this.props = props;
    }

    public boolean checkout(String orderId, String paymentId, String signature) {
        if (blank(orderId) || blank(paymentId) || blank(signature) || blank(props.keySecret())) {
            return false;
        }
        return matches(hmac((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), props.keySecret()), signature);
    }

    public boolean webhook(byte[] rawBody, String signature) {
        if (rawBody == null || blank(signature) || blank(props.webhookSecret())) {
            return false;
        }
        return matches(hmac(rawBody, props.webhookSecret()), signature);
    }

    private static boolean matches(String expectedHex, String givenHex) {
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8),
                givenHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
