package com.stagepass.common.crypto;

import com.stagepass.common.config.StagepassProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for organizer bank account numbers. Stored form: base64(iv ‖ ciphertext ‖ tag).
 * The key comes from {@code stagepass.payout-encryption-key} (base64, 32 bytes).
 */
@Component
public class PayoutCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PayoutCrypto(StagepassProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.payoutEncryptionKey() == null ? "" : props.payoutEncryptionKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("stagepass.payout-encryption-key is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("stagepass.payout-encryption-key must decode to 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored payout detail could not be decrypted", e);
        }
    }

    /** "••••" followed by the last four digits. */
    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "••••";
        }
        return "••••" + accountNumber.substring(accountNumber.length() - 4);
    }
}
