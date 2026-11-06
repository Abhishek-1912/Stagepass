package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.Refund;
import com.stagepass.booking.domain.RefundKind;
import com.stagepass.booking.domain.RefundStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.repository.RefundRepository;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.payment.domain.Payment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Money that arrives after the hold ended (spec Review Focus 4). If the tickets can still be taken the booking is
 * confirmed after all; otherwise the whole payment is refunded and the booking cancelled. Call inside the
 * transaction that locked the booking; submit the returned refund (if any) after commit.
 */
@Service
public class LatePaymentHandler {

    public static final String SOLD_OUT_REASON = "Tickets sold out before payment completed";

    /** {@code refundId} is set when a refund row was written and must be sent to Razorpay. */
    public record Result(boolean confirmed, UUID refundId) {
    }

    private static final Set<RefundStatus> LIVE = Set.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING,
            RefundStatus.PROCESSED, RefundStatus.PENDING_APPROVAL);

    private final BookingRepository bookings;
    private final RefundRepository refunds;
    private final InventoryService inventory;
    private final BookingConfirmationService confirmation;
    private final AuditLogService audit;
    private final EventRepository events;

    public LatePaymentHandler(BookingRepository bookings, RefundRepository refunds, InventoryService inventory,
            BookingConfirmationService confirmation, AuditLogService audit, EventRepository events) {
        this.events = events;
        this.bookings = bookings;
        this.refunds = refunds;
        this.inventory = inventory;
        this.confirmation = confirmation;
        this.audit = audit;
    }

    public Result handle(UUID bookingId, Payment payment) {
        Booking b = bookings.findForUpdate(bookingId).orElseThrow();
        boolean alreadyRefunding = refunds.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .anyMatch(r -> payment.getId().equals(r.getPaymentId()) && LIVE.contains(r.getStatus()));
        if (alreadyRefunding) {
            return new Result(false, null);
        }
        boolean eventOnSale = events.findById(b.getEventId()).map(e -> e.getStatus() == EventStatus.PUBLISHED).orElse(false);
        if (b.getStatus() == BookingStatus.EXPIRED && eventOnSale && holdAgain(b)) {
            confirmation.confirmLate(b, payment.getRazorpayPaymentId());
            return new Result(true, null);
        }

        if (b.getStatus() == BookingStatus.EXPIRED) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancelReason(SOLD_OUT_REASON);
            bookings.save(b);
        }
        Refund r = new Refund();
        r.setPaymentId(payment.getId());
        r.setBookingId(b.getId());
        r.setKind(RefundKind.LATE_PAYMENT);
        r.setAmountPaise(payment.getAmountPaise());
        r.setFeePaise(b.getFeePaise());
        r.setFeeGstPaise(b.getFeeGstPaise());
        r.setReason(b.getCancelReason() != null ? b.getCancelReason() : "Payment received for a closed booking");
        r.setStatus(RefundStatus.REQUESTED);
        refunds.saveAndFlush(r);
        audit.record(null, "REFUND_REQUESTED", "booking", b.getId().toString(), null,
                Map.of("kind", "LATE_PAYMENT", "amountPaise", payment.getAmountPaise(), "refundId", r.getId().toString()));
        return new Result(false, r.getId());
    }

    /** Takes every item's tickets again, all or nothing. */
    private boolean holdAgain(Booking b) {
        List<BookingItem> taken = new ArrayList<>();
        for (BookingItem item : b.getItems()) {
            if (!inventory.hold(item.getTicketTypeId(), item.getQuantity())) {
                taken.forEach(t -> inventory.release(t.getTicketTypeId(), t.getQuantity()));
                return false;
            }
            taken.add(item);
        }
        return true;
    }
}
