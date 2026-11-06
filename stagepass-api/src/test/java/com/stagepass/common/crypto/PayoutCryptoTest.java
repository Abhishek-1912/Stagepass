package com.stagepass.common.crypto;

import com.stagepass.common.config.StagepassProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutCryptoTest {

    private static StagepassProperties props(String key) {
        return new StagepassProperties("http://localhost", new StagepassProperties.Cors(List.of()),
                new StagepassProperties.Bootstrap(List.of()), 10, "q", key, new StagepassProperties.Payout(3));
    }

    private final PayoutCrypto crypto = new PayoutCrypto(props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));

    @Test
    void roundTripsAndUsesRandomIv() {
        String a = crypto.encrypt("123456789012");
        String b = crypto.encrypt("123456789012");

        assertThat(a).isNotEqualTo(b).doesNotContain("123456789012");
        assertThat(crypto.decrypt(a)).isEqualTo("123456789012");
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] raw = Base64.getDecoder().decode(crypto.encrypt("123456789012"));
        raw[raw.length - 1] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void masksToLastFour() {
        assertThat(PayoutCrypto.mask("123456789012")).isEqualTo("••••9012");
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> new PayoutCrypto(props(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }
}
