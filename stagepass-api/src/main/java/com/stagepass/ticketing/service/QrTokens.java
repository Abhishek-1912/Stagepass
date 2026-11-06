package com.stagepass.ticketing.service;

import com.stagepass.common.config.StagepassProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed QR tokens (spec 8.5): {@code base64url(ticketId|eventId|qrNonce) + "." + base64url(HMAC-SHA256)}, the MAC
 * taken over the encoded payload with {@code stagepass.qr-secret}. No personal data; tampering breaks the signature.
 */
@Component
public class QrTokens {

    private static final int MAX_TOKEN_LENGTH = 512;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;

    @Autowired
    public QrTokens(StagepassProperties props) {
        this(props.qrSecret());
    }

    public QrTokens(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("stagepass.qr-secret must be set to at least 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String sign(UUID ticketId, UUID eventId, String nonce) {
        String payload = B64.encodeToString((ticketId + "|" + eventId + "|" + nonce).getBytes(StandardCharsets.UTF_8));
        return payload + "." + B64.encodeToString(mac(payload));
    }

    /** The claims of a genuine token; empty for anything forged, altered or malformed. Never throws. */
    public Optional<QrClaims> verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String payload = token.substring(0, dot);
        try {
            byte[] given = Base64.getUrlDecoder().decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(given, mac(payload))) {
                return Optional.empty();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || parts[2].isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new QrClaims(UUID.fromString(parts[0]), UUID.fromString(parts[1]), parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] mac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
