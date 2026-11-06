package com.stagepass.payment.service;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.service.BookingConfirmationService;
import com.stagepass.booking.service.BookingService;
import com.stagepass.booking.service.LatePaymentHandler;
import com.stagepass.booking.service.RefundGateway;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.payment.api.VerifyPaymentRequest;
import com.stagepass.payment.domain.Payment;
import com.stagepass.payment.domain.PaymentStatus;
import com.stagepass.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Settles Razorpay payments. A booking is confirmed only after the payment fetched from Razorpay itself is
 * captured for exactly the booking's amount in INR on the booking's order; browser success alone never confirms.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public enum Outcome {
        CONFIRMED,
        ALREADY_CONFIRMED,
        /** Money arrived for a booking that is no longer pending, or a second payment on the same order. */
        LATE_PAYMENT,
        /** Wrong amount, currency or status: nothing was confirmed. */
        MISMATCH,
        /** The payment's order is not one of ours. */
        UNKNOWN_ORDER
    }

    public record SettleResult(Outcome outcome, UUID bookingId, String reason) {
    }

    private final RazorpayClient razorpay;
    private final RazorpaySignatures signatures;
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final BookingConfirmationService confirmation;
    private final BookingService bookingService;
    private final AuditLogService audit;
    private final TransactionTemplate tx;
    private final LatePaymentHandler latePayments;
    private final RefundGateway refundGateway;

    public PaymentService(RazorpayClient razorpay, RazorpaySignatures signatures, PaymentRepository payments,
            BookingRepository bookings, BookingConfirmationService confirmation, BookingService bookingService,
            AuditLogService audit, PlatformTransactionManager txManager, LatePaymentHandler latePayments,
            RefundGateway refundGateway) {
        this.latePayments = latePayments;
        this.refundGateway = refundGateway;
        this.razorpay = razorpay;
        this.signatures = signatures;
        this.payments = payments;
        this.bookings = bookings;
        this.confirmation = confirmation;
        this.bookingService = bookingService;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Browser callback after Razorpay Checkout: check the signature, then settle from Razorpay's own record. */
    public BookingResponse verify(UUID userId, VerifyPaymentRequest r) {
        Booking booking = bookings.findByIdAndUserId(r.bookingId(), userId)
                .orElseThrow(() -> ApiException.notFound("Booking"));
        Optional<Payment> row = payments.findByRazorpayOrderId(r.razorpayOrderId())
                .filter(p -> p.getBookingId().equals(booking.getId()));
        if (row.isEmpty()) {
            mismatch(userId, booking.getId(), "order " + r.razorpayOrderId() + " does not belong to this booking");
            throw FieldErrors.single("razorpayOrderId", "does not match this booking");
        }
        if (!signatures.checkout(r.razorpayOrderId(), r.razorpayPaymentId(), r.razorpaySignature())) {
            mismatch(userId, booking.getId(), "bad checkout signature for payment " + r.razorpayPaymentId());
            throw FieldErrors.single("razorpaySignature", "payment could not be verified");
        }
        SettleResult result = settle(r.razorpayPaymentId());
        if (result.outcome() == Outcome.MISMATCH || result.outcome() == Outcome.UNKNOWN_ORDER
                || !booking.getId().equals(result.bookingId())) {
            throw FieldErrors.single("razorpayPaymentId", "payment could not be verified");
        }
        return bookingService.view(booking.getId());
    }

    /** Idempotent: safe to call any number of times, concurrently, from the verify call and from webhooks. */
    public SettleResult settle(String razorpayPaymentId) {
        RazorpayModels.Payment rp = fetch(() -> razorpay.fetchPayment(razorpayPaymentId));
        if (rp == null) {
            return new SettleResult(Outcome.UNKNOWN_ORDER, null, "payment not found at Razorpay");
        }
        Payment row = rp.orderId() == null ? null : payments.findByRazorpayOrderId(rp.orderId()).orElse(null);
        if (row == null) {
            audit.record(null, "PAYMENT_MISMATCH", "payment", razorpayPaymentId, null,
                    Map.of("reason", "unknown order " + rp.orderId()));
            return new SettleResult(Outcome.UNKNOWN_ORDER, null, "unknown order");
        }

        String problem = problemWith(rp, row);
        if (problem == null && "authorized".equals(rp.status())) {
            RazorpayModels.Payment captured = fetch(() -> razorpay.capture(rp.id(), row.getAmountPaise()));
            if (captured == null) {
                // Usually a concurrent settle captured it first: settle from Razorpay's current record.
                RazorpayModels.Payment now = fetch(() -> razorpay.fetchPayment(rp.id()));
                if (now != null && "captured".equals(now.status())) {
                    return record(row, now, problemWith(now, row));
                }
                problem = "capture was refused";
            } else {
                return record(row, captured, problemWith(captured, row));
            }
        }
        return record(row, rp, problem);
    }

    private SettleResult record(Payment row, RazorpayModels.Payment rp, String problem) {
        if (problem == null && !"captured".equals(rp.status())) {
            problem = "payment status is " + rp.status();
        }
        if (problem != null) {
            String reason = problem;
            tx.executeWithoutResult(s -> {
                bookings.findForUpdate(row.getBookingId());
                Payment p = payments.findById(row.getId()).orElseThrow();
                if (p.getStatus() != PaymentStatus.CAPTURED) {
                    p.setStatus(PaymentStatus.FAILED);
                    p.setFailureReason(reason);
                    payments.save(p);
                }
                mismatch(null, row.getBookingId(), reason + " (payment " + rp.id() + ")");
            });
            return new SettleResult(Outcome.MISMATCH, row.getBookingId(), problem);
        }

        UUID[] refundToSend = new UUID[1];
        SettleResult result = tx.execute(s -> {
            // Lock the booking first: every settle of this booking runs one at a time from here.
            bookings.findForUpdate(row.getBookingId());
            Payment p = payments.findById(row.getId()).orElseThrow();
            if (p.getStatus() == PaymentStatus.CAPTURED && !Objects.equals(p.getRazorpayPaymentId(), rp.id())) {
                log.error("Second captured payment {} on order {} (first {}): refund it from the Razorpay dashboard",
                        rp.id(), rp.orderId(), p.getRazorpayPaymentId());
                mismatch(null, row.getBookingId(), "second captured payment " + rp.id() + " on the same order");
                return new SettleResult(Outcome.LATE_PAYMENT, row.getBookingId(), "second payment on the same order");
            }
            p.setRazorpayPaymentId(rp.id());
            p.setStatus(PaymentStatus.CAPTURED);
            p.setMethod(rp.method());
            p.setGatewayFeePaise(rp.fee());
            p.setGatewayTaxPaise(rp.tax());
            p.setRaw(rp.rawJson());
            p.setFailureReason(null);
            payments.save(p);
            return switch (confirmation.confirmPaid(row.getBookingId(), rp.id())) {
                case CONFIRMED -> new SettleResult(Outcome.CONFIRMED, row.getBookingId(), null);
                case ALREADY_CONFIRMED -> new SettleResult(Outcome.ALREADY_CONFIRMED, row.getBookingId(), null);
                case NOT_PENDING -> {
                    LatePaymentHandler.Result late = latePayments.handle(row.getBookingId(), p);
                    refundToSend[0] = late.refundId();
                    yield late.confirmed() ? new SettleResult(Outcome.CONFIRMED, row.getBookingId(), "confirmed late")
                            : new SettleResult(Outcome.LATE_PAYMENT, row.getBookingId(), "booking no longer pending");
                }
            };
        });
        if (refundToSend[0] != null) {
            refundGateway.submit(refundToSend[0]);
        }
        return result;
    }

    private static String problemWith(RazorpayModels.Payment rp, Payment row) {
        if (!row.getRazorpayOrderId().equals(rp.orderId())) {
            return "order is " + rp.orderId() + ", expected " + row.getRazorpayOrderId();
        }
        if (!"INR".equals(rp.currency())) {
            return "currency is " + rp.currency();
        }
        if (rp.amount() != row.getAmountPaise()) {
            return "amount is " + rp.amount() + " paise, expected " + row.getAmountPaise();
        }
        return null;
    }

    private void mismatch(UUID actorId, UUID bookingId, String reason) {
        log.warn("Payment mismatch on booking {}: {}", bookingId, reason);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        audit.record(actorId, "PAYMENT_MISMATCH", "booking", bookingId.toString(), null, details);
    }

    /** Razorpay call: retryable failures become 503; a refusal (4xx) becomes null. */
    private static RazorpayModels.Payment fetch(java.util.function.Supplier<RazorpayModels.Payment> call) {
        try {
            return call.get();
        } catch (RazorpayException e) {
            if (e.retryable()) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Could not reach the payment gateway, please retry");
            }
            log.warn("Razorpay refused: {}", e.getMessage());
            return null;
        }
    }
}
