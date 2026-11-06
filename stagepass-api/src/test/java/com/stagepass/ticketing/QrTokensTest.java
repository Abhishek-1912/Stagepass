package com.stagepass.ticketing;

import com.stagepass.ticketing.service.QrClaims;
import com.stagepass.ticketing.service.QrTokens;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrTokensTest {

    private static final String SECRET = "test-qr-secret-test-qr-secret-0000";
    private final QrTokens tokens = new QrTokens(SECRET);
    private final UUID ticket = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();

    @Test
    void roundTrips() {
        assertThat(tokens.verify(tokens.sign(ticket, event, "N0NCE"))).contains(new QrClaims(ticket, event, "N0NCE"));
    }

    @Test
    void tokenHasNoPii() {
        String token = tokens.sign(ticket, event, "N0NCE");
        assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}$");
        String payload = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))), StandardCharsets.UTF_8);
        assertThat(payload).isEqualTo(ticket + "|" + event + "|N0NCE");
    }

    @Test
    void alteredPayloadFails() {
        String token = tokens.sign(ticket, event, "N0NCE");
        String signature = token.substring(token.indexOf('.'));
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((UUID.randomUUID() + "|" + event + "|N0NCE").getBytes(StandardCharsets.UTF_8));
        assertThat(tokens.verify(forged + signature)).isEmpty();
    }

    @Test
    void otherSecretFails() {
        String foreign = new QrTokens("another-secret-another-secret-9999").sign(ticket, event, "N0NCE");
        assertThat(tokens.verify(foreign)).isEmpty();
    }

    @Test
    void malformedNeverThrows() {
        for (String bad : new String[] {"", "abc", "a.b", "....", null, "x".repeat(10_000), "%%%.%%%",
                Base64.getUrlEncoder().encodeToString("only|two".getBytes()) + ".AAAA"}) {
            assertThat(tokens.verify(bad)).as(String.valueOf(bad).substring(0, Math.min(20, String.valueOf(bad).length())))
                    .isEmpty();
        }
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> new QrTokens("short")).isInstanceOf(IllegalStateException.class)
                .hasMessage("stagepass.qr-secret must be set to at least 32 characters");
        assertThatThrownBy(() -> new QrTokens((String) null)).isInstanceOf(IllegalStateException.class);
    }
}
