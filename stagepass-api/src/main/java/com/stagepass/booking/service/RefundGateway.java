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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Sends approved refund rows to Razorpay and records the outcome. {@link #submit} must run outside any
 * transaction (it calls Razorpay); the refund row was committed beforehand with status {@code REQUESTED}.
 */
@Service
public class RefundGateway {

    private static final Logger log = LoggerFactory.getLogger(RefundGateway.class);

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
        Payment p = payments.findById(r.getPaymentId()).orElseThrow();
        try {
            RazorpayModels.Refund rr = razorpay.refund(p.getRazorpayPaymentId(), r.getAmountPaise(),
                    "refund-" + r.getId(), Map.of("refundId", r.getId().toString(), "bookingId", r.getBookingId().toString()));
            return tx.execute(s -> finish(refundId,
                    "processed".equals(rr.status()) ? RefundStatus.PROCESSED : RefundStatus.PROCESSING, rr.id(), null));
        } catch (RazorpayException e) {
            log.warn("Refund {} was not accepted by Razorpay: {}", refundId, e.getMessage());
            return tx.execute(s -> finish(refundId, RefundStatus.FAILED, null, e.getMessage()));
        }
    }

    /** Webhook {@code refund.processed}. */
    public void onProcessed(String razorpayRefundId) {
        tx.executeWithoutResult(s -> refunds.findByRazorpayRefundId(razorpayRefundId)
                .filter(r -> r.getStatus() != RefundStatus.PROCESSED)
                .ifPresent(r -> finish(r.getId(), RefundStatus.PROCESSED, razorpayRefundId, null)));
    }

    /** Webhook {@code refund.failed}. */
    public void onFailed(String razorpayRefundId, String reason) {
        tx.executeWithoutResult(s -> refunds.findByRazorpayRefundId(razorpayRefundId)
                .filter(r -> r.getStatus() != RefundStatus.PROCESSED && r.getStatus() != RefundStatus.FAILED)
                .ifPresent(r -> finish(r.getId(), RefundStatus.FAILED, razorpayRefundId, reason)));
    }

    private RefundStatus finish(UUID refundId, RefundStatus status, String razorpayRefundId, String failure) {
        Refund r = refunds.findById(refundId).orElseThrow();
        r.setStatus(status);
        if (razorpayRefundId != null) {
            r.setRazorpayRefundId(razorpayRefundId);
        }
        r.setFailureReason(failure);
        refunds.save(r);
        if (status == RefundStatus.PROCESSED || status == RefundStatus.FAILED) {
            audit.record(null, status == RefundStatus.PROCESSED ? "REFUND_PROCESSED" : "REFUND_FAILED", "refund",
                    r.getId().toString(), null, failure == null ? Map.of("amountPaise", r.getAmountPaise())
                            : Map.of("amountPaise", r.getAmountPaise(), "reason", failure));
        }
        return status;
    }
}
