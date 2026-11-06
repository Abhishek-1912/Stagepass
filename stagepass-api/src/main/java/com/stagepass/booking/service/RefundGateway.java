package com.stagepass.booking.service;

import com.stagepass.booking.domain.Refund;
import com.stagepass.booking.domain.RefundStatus;
import com.stagepass.booking.repository.RefundRepository;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.payment.domain.Payment;
import com.stagepass.payment.repository.PaymentRepository;
import com.stagepass.payment.service.RazorpayClient;
import com.stagepass.payment.service.RazorpayException;
import com.stagepass.payment.service.RazorpayModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends approved refund rows to Razorpay and records the outcome. {@link #submit} must run outside any
 * transaction (it calls Razorpay); the refund row was committed beforehand with status {@code REQUESTED}.
 *
 * <p>A refund call that gets no answer (timeout, 5xx) may still have been carried out, so it is never treated as
 * failed: the row stays {@code PROCESSING} without a Razorpay id and {@link #reconcile} later looks it up by the
 * {@code notes.refundId} we sent. Only a definite refusal (4xx) is {@code FAILED}.
 */
@Service
public class RefundGateway {

    private static final Logger log = LoggerFactory.getLogger(RefundGateway.class);
    static final String UNCONFIRMED = "Razorpay did not answer; confirming the refund";

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final RazorpayClient razorpay;
    private final AuditLogService audit;
    private final TransactionTemplate tx;

    public RefundGateway(RefundRepository refunds, PaymentRepository payments, RazorpayClient razorpay,
            AuditLogService audit, PlatformTransactionManager txManager) {
        this.refunds = refunds;
        this.payments = payments;
        this.razorpay = razorpay;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        // Also called from after-commit callbacks, where a joined transaction would never commit.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Asks Razorpay to refund a REQUESTED row. ₹0 rows (free bookings) complete without a gateway call. */
    public RefundStatus submit(UUID refundId) {
        Refund r = refunds.findById(refundId).orElseThrow();
        if (r.getStatus() != RefundStatus.REQUESTED) {
            return r.getStatus();
        }
        if (r.getAmountPaise() == 0 || r.getPaymentId() == null) {
            return tx.execute(s -> finish(refundId, RefundStatus.PROCESSED, null, null));
        }
        tx.executeWithoutResult(s -> {
            Refund row = refunds.findById(refundId).orElseThrow();
            row.setAttempts(row.getAttempts() + 1);
            refunds.save(row);
        });
        Payment p = payments.findById(r.getPaymentId()).orElseThrow();
        try {
            RazorpayModels.Refund rr = razorpay.refund(p.getRazorpayPaymentId(), r.getAmountPaise(),
                    "refund-" + r.getId(), Map.of("refundId", r.getId().toString(), "bookingId", r.getBookingId().toString()));
            return tx.execute(s -> finish(refundId, statusOf(rr.status()), rr.id(), null));
        } catch (RazorpayException e) {
            if (e.retryable()) {
                log.warn("Refund {} got no answer from Razorpay ({}); will reconcile", refundId, e.getMessage());
                return tx.execute(s -> finish(refundId, RefundStatus.PROCESSING, null, UNCONFIRMED));
            }
            log.warn("Refund {} was refused by Razorpay: {}", refundId, e.getMessage());
            return tx.execute(s -> finish(refundId, RefundStatus.FAILED, null, e.getMessage()));
        }
    }

    /**
     * For a PROCESSING row whose refund call went unanswered: finds the refund at Razorpay by our id and records it,
     * or, if Razorpay never received it, sends it again. Returns the resulting status. Call outside a transaction.
     */
    public RefundStatus reconcile(UUID refundId) {
        Refund r = refunds.findById(refundId).orElseThrow();
        if (r.getStatus() != RefundStatus.PROCESSING || r.getRazorpayRefundId() != null) {
            return r.getStatus();
        }
        Payment p = payments.findById(r.getPaymentId()).orElseThrow();
        Optional<RazorpayModels.RefundRecord> found;
        try {
            found = razorpay.listRefunds(p.getRazorpayPaymentId()).stream()
                    .filter(x -> refundId.toString().equals(x.ourRefundId())).findFirst();
        } catch (RazorpayException e) {
            log.warn("Could not reconcile refund {} yet: {}", refundId, e.getMessage());
            return RefundStatus.PROCESSING;
        }
        if (found.isPresent()) {
            RazorpayModels.RefundRecord rr = found.get();
            return tx.execute(s -> finish(refundId, statusOf(rr.status()), rr.id(), null));
        }
        tx.executeWithoutResult(s -> {
            Refund row = refunds.findById(refundId).orElseThrow();
            row.setStatus(RefundStatus.REQUESTED);
            refunds.save(row);
        });
        return submit(refundId);
    }

    /** Webhook {@code refund.processed}; {@code ourRefundId} is the {@code notes.refundId} we sent, if any. */
    public void onProcessed(String razorpayRefundId, String ourRefundId) {
        tx.executeWithoutResult(s -> locate(razorpayRefundId, ourRefundId)
                .filter(r -> r.getStatus() != RefundStatus.PROCESSED)
                .ifPresent(r -> finish(r.getId(), RefundStatus.PROCESSED, razorpayRefundId, null)));
    }

    /** Webhook {@code refund.failed}; returns the refund it marked failed, if any (the caller decides what's next). */
    public Optional<UUID> onFailed(String razorpayRefundId, String ourRefundId, String reason) {
        return tx.execute(s -> locate(razorpayRefundId, ourRefundId)
                .filter(r -> r.getStatus() == RefundStatus.PROCESSING || r.getStatus() == RefundStatus.REQUESTED)
                .map(r -> {
                    finish(r.getId(), RefundStatus.FAILED, razorpayRefundId, reason);
                    return r.getId();
                }));
    }

    private Optional<Refund> locate(String razorpayRefundId, String ourRefundId) {
        Optional<Refund> byRazorpay = razorpayRefundId == null || razorpayRefundId.isBlank() ? Optional.empty()
                : refunds.findByRazorpayRefundId(razorpayRefundId);
        if (byRazorpay.isPresent() || ourRefundId == null || ourRefundId.isBlank()) {
            return byRazorpay;
        }
        try {
            return refunds.findById(UUID.fromString(ourRefundId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static RefundStatus statusOf(String razorpayStatus) {
        return switch (razorpayStatus == null ? "" : razorpayStatus) {
            case "processed" -> RefundStatus.PROCESSED;
            case "failed" -> RefundStatus.FAILED;
            default -> RefundStatus.PROCESSING;
        };
    }

    private RefundStatus finish(UUID refundId, RefundStatus status, String razorpayRefundId, String failure) {
        Refund r = refunds.findById(refundId).orElseThrow();
        r.setStatus(status);
        if (razorpayRefundId != null) {
            r.setRazorpayRefundId(razorpayRefundId);
        }
        r.setFailureReason(failure);
        if (status != RefundStatus.FAILED) {
            r.setNeedsRetry(false);
        }
        refunds.save(r);
        if (status == RefundStatus.PROCESSED || status == RefundStatus.FAILED) {
            audit.record(null, status == RefundStatus.PROCESSED ? "REFUND_PROCESSED" : "REFUND_FAILED", "refund",
                    r.getId().toString(), null, failure == null ? Map.of("amountPaise", r.getAmountPaise())
                            : Map.of("amountPaise", r.getAmountPaise(), "reason", failure));
        }
        return status;
    }
}
