package com.stagepass.payment.service;

import com.stagepass.booking.service.RefundGateway;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.payment.domain.Dispute;
import com.stagepass.payment.domain.Payment;
import com.stagepass.payment.domain.PaymentStatus;
import com.stagepass.payment.repository.DisputeRepository;
import com.stagepass.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Razorpay webhooks. The signature is checked on the raw bytes before anything is parsed or stored. Each event id
 * is processed once; a processing error answers 500 so Razorpay delivers it again.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PROVIDER = "razorpay";

    private final RazorpaySignatures signatures;
    private final PaymentService paymentService;
    private final PaymentRepository payments;
    private final DisputeRepository disputes;
    private final RefundGateway refunds;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public WebhookService(RazorpaySignatures signatures, PaymentService paymentService, PaymentRepository payments,
            DisputeRepository disputes, RefundGateway refunds, AuditLogService audit, JdbcTemplate jdbc,
            PlatformTransactionManager txManager) {
        this.signatures = signatures;
        this.paymentService = paymentService;
        this.payments = payments;
        this.disputes = disputes;
        this.refunds = refunds;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public void handle(byte[] rawBody, String signature, String eventIdHeader) {
        if (!signatures.webhook(rawBody, signature)) {
            log.warn("Rejected Razorpay webhook with a missing or invalid signature");
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = JSON.readTree(rawBody);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Webhook body is not JSON");
        }
        String type = root.path("event").asString("");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader : "sha256:" + sha256(rawBody);

        jdbc.update("""
                insert into webhook_events (provider, event_id, type, payload) values (?, ?, ?, ?::jsonb)
                on conflict (provider, event_id) do nothing
                """, PROVIDER, eventId, type, new String(rawBody, StandardCharsets.UTF_8));
        List<Object> processed = jdbc.queryForList(
                "select processed_at from webhook_events where provider = ? and event_id = ?", Object.class, PROVIDER, eventId);
        if (!processed.isEmpty() && processed.get(0) != null) {
            log.debug("Razorpay webhook {} already processed", eventId);
            return;
        }

        try {
            dispatch(type, root.path("payload"));
        } catch (RuntimeException e) {
            log.error("Razorpay webhook {} ({}) failed; Razorpay will retry", eventId, type, e);
            throw new ApiException(ErrorCode.INTERNAL, "Webhook processing failed");
        }
        jdbc.update("update webhook_events set processed_at = now() where provider = ? and event_id = ?", PROVIDER, eventId);
    }

    private void dispatch(String type, JsonNode payload) {
        switch (type) {
            case "payment.captured", "payment.authorized", "order.paid" -> {
                String paymentId = payload.path("payment").path("entity").path("id").asString("");
                if (!paymentId.isEmpty()) {
                    PaymentService.SettleResult r = paymentService.settle(paymentId);
                    log.info("Webhook {} for {}: {}", type, paymentId, r.outcome());
                }
            }
            case "payment.failed" -> paymentFailed(payload.path("payment").path("entity"));
            case "refund.processed" -> refunds.onProcessed(payload.path("refund").path("entity").path("id").asString());
            case "refund.failed" -> {
                JsonNode refund = payload.path("refund").path("entity");
                refunds.onFailed(refund.path("id").asString(), "Razorpay reported the refund failed");
            }
            case "payment.dispute.created", "payment.dispute.won", "payment.dispute.lost", "payment.dispute.closed",
                 "payment.dispute.under_review", "payment.dispute.action_required" ->
                    dispute(payload.path("dispute").path("entity"), type);
            default -> log.debug("Ignoring Razorpay webhook {}", type);
        }
    }

    private void paymentFailed(JsonNode entity) {
        String orderId = entity.path("order_id").asString("");
        String reason = entity.path("error_description").asString("Payment failed");
        tx.executeWithoutResult(s -> payments.findByRazorpayOrderId(orderId)
                .filter(p -> p.getStatus() == PaymentStatus.CREATED || p.getStatus() == PaymentStatus.FAILED)
                .ifPresent(p -> {
                    // The booking stays pending until its hold ends, so the buyer can try again on the same order.
                    p.setStatus(PaymentStatus.FAILED);
                    p.setFailureReason(reason);
                    payments.save(p);
                }));
    }

    private void dispute(JsonNode entity, String type) {
        String disputeId = entity.path("id").asString("");
        String paymentId = entity.path("payment_id").asString("");
        tx.executeWithoutResult(s -> {
            Payment payment = payments.findByRazorpayPaymentId(paymentId).orElse(null);
            if (payment == null) {
                log.warn("Dispute {} is for unknown payment {}", disputeId, paymentId);
                return;
            }
            Dispute d = disputes.findByRazorpayDisputeId(disputeId).orElseGet(Dispute::new);
            d.setRazorpayDisputeId(disputeId);
            d.setPaymentId(payment.getId());
            d.setAmountPaise(entity.path("amount").asLong());
            d.setReason(entity.path("reason_code").asString(entity.path("reason_description").asString(null)));
            d.setStatus(entity.path("status").asString(type.substring("payment.dispute.".length())));
            d.setRaw(entity.toString());
            disputes.save(d);
            audit.record(null, "DISPUTE_RECORDED", "booking", payment.getBookingId().toString(), null,
                    Map.of("disputeId", disputeId, "status", d.getStatus(), "event", type));
        });
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
