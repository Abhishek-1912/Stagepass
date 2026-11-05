package com.stagepass.notification;

import com.stagepass.notification.service.EmailDispatcher;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailRenderer;
import com.stagepass.notification.service.EmailTemplate;
import com.stagepass.notification.service.ResendClient;
import com.stagepass.notification.config.ResendProperties;
import com.stagepass.support.FakeResendServer;
import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDispatcherTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    EmailOutbox outbox;

    @Autowired
    EmailRenderer renderer;

    FakeResendServer resend;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from email_outbox");
        resend = new FakeResendServer();
    }

    @AfterEach
    void tearDown() {
        resend.close();
    }

    private EmailDispatcher dispatcher(String apiKey) {
        ResendProperties props = new ResendProperties(apiKey, "Stagepass <onboarding@resend.dev>", resend.baseUrl());
        return new EmailDispatcher(jdbc, txManager, new ResendClient(props), renderer);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select status, attempts, send_after, sent_at, last_error from email_outbox where id = ?", id);
    }

    @Test
    void sendsPendingEmailAndMarksSent() {
        UUID id = outbox.enqueue("asha@test.in", EmailTemplate.WELCOME, Map.of("name", "Asha"));

        int processed = dispatcher("re_test_key").dispatchBatch();

        assertThat(processed).isEqualTo(1);
        assertThat(row(id).get("status")).isEqualTo("SENT");
        assertThat(row(id).get("sent_at")).isNotNull();
        FakeResendServer.Received req = resend.received().get(0);
        assertThat(req.authorization()).isEqualTo("Bearer re_test_key");
        assertThat(req.idempotencyKey()).isEqualTo(id.toString());
        assertThat(req.body()).contains("\"to\":[\"asha@test.in\"]").contains("Welcome to Stagepass");
    }

    @Test
    void serverErrorIsRetriedLaterWithBackoff() {
        resend.respondWith(500);
        UUID id = outbox.enqueue("ravi@test.in", EmailTemplate.WELCOME, Map.of("name", "Ravi"));

        dispatcher("re_test_key").dispatchBatch();

        Map<String, Object> r = row(id);
        assertThat(r.get("status")).isEqualTo("PENDING");
        assertThat(r.get("attempts")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select send_after > now() + interval '30 seconds' from email_outbox where id = ?",
                Boolean.class, id)).isTrue();
        assertThat(r.get("last_error")).asString().contains("500");
    }

    @Test
    void rejectedAddressFailsWithoutRetry() {
        resend.respondWith(422);
        UUID id = outbox.enqueue("not-an-inbox", EmailTemplate.WELCOME, Map.of("name", "X"));

        dispatcher("re_test_key").dispatchBatch();

        assertThat(row(id).get("status")).isEqualTo("FAILED");
    }

    @Test
    void givesUpAfterMaxAttempts() {
        resend.respondWith(503);
        UUID id = outbox.enqueue("late@test.in", EmailTemplate.WELCOME, Map.of("name", "L"));
        jdbc.update("update email_outbox set attempts = 7 where id = ?", id);

        dispatcher("re_test_key").dispatchBatch();

        assertThat(row(id).get("status")).isEqualTo("FAILED");
        assertThat(row(id).get("attempts")).isEqualTo(8);
    }

    @Test
    void withoutApiKeyEmailsStayQueued() {
        UUID id = outbox.enqueue("queued@test.in", EmailTemplate.WELCOME, Map.of("name", "Q"));

        int processed = dispatcher("").dispatchBatch();

        assertThat(processed).isZero();
        assertThat(row(id).get("status")).isEqualTo("PENDING");
        assertThat(row(id).get("attempts")).isEqualTo(0);
        assertThat(resend.received()).isEmpty();
    }

    @Test
    void futureEmailsAreNotSentYet() {
        UUID id = outbox.enqueue("later@test.in", EmailTemplate.WELCOME, Map.of("name", "Later"));
        jdbc.update("update email_outbox set send_after = now() + interval '1 hour' where id = ?", id);

        assertThat(dispatcher("re_test_key").dispatchBatch()).isZero();
        assertThat(row(id).get("status")).isEqualTo("PENDING");
    }
}
