package com.stagepass.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends queued emails. Each email is claimed with {@code FOR UPDATE SKIP LOCKED} in its own transaction, so several
 * API instances can run the dispatcher without double-sending. Failures retry with exponential backoff
 * (1, 2, 4 … capped at 60 minutes) up to {@value #MAX_ATTEMPTS} attempts; addresses Resend rejects (4xx) fail at once.
 */
@Service
public class EmailDispatcher {

    static final int MAX_ATTEMPTS = 8;
    private static final int BATCH_SIZE = 25;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ResendClient client;
    private final EmailRenderer renderer;
    private final java.util.List<EmailAttachmentProvider> attachmentProviders;
    private final TransactionTemplate attachmentTx;
    private final AtomicBoolean warnedUnconfigured = new AtomicBoolean();

    public EmailDispatcher(JdbcTemplate jdbc, PlatformTransactionManager txManager, ResendClient client,
            EmailRenderer renderer, java.util.List<EmailAttachmentProvider> attachmentProviders) {
        this.attachmentProviders = attachmentProviders;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.attachmentTx = new TransactionTemplate(txManager);
        this.attachmentTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.attachmentTx.setReadOnly(true);
        this.client = client;
        this.renderer = renderer;
    }

    /** Sends up to one batch of due emails; returns how many were attempted. */
    public int dispatchBatch() {
        if (!client.configured()) {
            if (warnedUnconfigured.compareAndSet(false, true)) {
                log.warn("RESEND_API_KEY is not set: emails stay queued in email_outbox until it is configured");
            }
            return 0;
        }
        int processed = 0;
        while (processed < BATCH_SIZE && Boolean.TRUE.equals(tx.execute(status -> sendNext()))) {
            processed++;
        }
        return processed;
    }

    private boolean sendNext() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, to_address, template, payload::text as payload, attempts
                from email_outbox
                where status = 'PENDING' and send_after <= now()
                order by send_after
                limit 1
                for update skip locked
                """);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        UUID id = (UUID) row.get("id");
        int attempts = ((Number) row.get("attempts")).intValue() + 1;

        ResendClient.Result result;
        try {
            EmailTemplate template = EmailTemplate.valueOf((String) row.get("template"));
            Map<String, Object> data = JSON.readValue((String) row.get("payload"), new TypeReference<>() { });
            RenderedEmail email = renderer.render(template, data);
            java.util.List<EmailAttachment> files;
            try {
                files = attachments(template, data);
            } catch (RuntimeException e) {
                log.warn("Attachments for email {} failed (attempt {}): {}", id, attempts, e.getMessage());
                files = null;
                if (attempts < MAX_ATTEMPTS) {
                    // Try again later; a broken attachment must not fail the email or hold up the queue.
                    retryLater(id, attempts, "Attachment failed: " + e.getMessage());
                    return true;
                }
            }
            // On the last attempt, a missing attachment is better than no email.
            result = client.send(id.toString(), (String) row.get("to_address"),
                    files == null ? email : email.withAttachments(files));
        } catch (RuntimeException e) {
            result = new ResendClient.Result(ResendClient.Outcome.REJECTED, "Render failed: " + e.getMessage());
        }

        switch (result.outcome()) {
            case SENT -> jdbc.update("""
                    update email_outbox set status = 'SENT', attempts = ?, sent_at = now(), last_error = null where id = ?
                    """, attempts, id);
            case RETRYABLE -> {
                if (attempts >= MAX_ATTEMPTS) {
                    fail(id, attempts, result.error());
                } else {
                    retryLater(id, attempts, result.error());
                }
            }
            case REJECTED -> fail(id, attempts, result.error());
        }
        return true;
    }

    private void retryLater(UUID id, int attempts, String error) {
        jdbc.update("""
                update email_outbox
                set attempts = ?, last_error = ?,
                    send_after = now() + make_interval(mins => least(power(2, ? - 1)::int, 60))
                where id = ?
                """, attempts, error, attempts, id);
    }

    /**
     * Builds attachments in their own read-only transaction, so a failure inside a provider (which may be
     * transactional itself) can't mark the claim transaction rollback-only and stall the queue.
     */
    private List<EmailAttachment> attachments(EmailTemplate template, Map<String, Object> data) {
        if (attachmentProviders.stream().noneMatch(p -> p.supports(template))) {
            return List.of();
        }
        return attachmentTx.execute(s -> {
            List<EmailAttachment> files = new java.util.ArrayList<>();
            for (EmailAttachmentProvider p : attachmentProviders) {
                if (p.supports(template)) {
                    files.addAll(p.attachmentsFor(template, data));
                }
            }
            return files;
        });
    }

    private void fail(UUID id, int attempts, String error) {
        log.warn("Email {} failed permanently after {} attempt(s): {}", id, attempts, error);
        jdbc.update("update email_outbox set status = 'FAILED', attempts = ?, last_error = ? where id = ?",
                attempts, error, id);
    }
}
