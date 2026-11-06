package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a pending booking into a confirmed one: held tickets become sold, tickets are issued, the organizer
 * ledger and GST invoice are written. Every entry point locks the booking row first, so the browser callback,
 * webhooks and retries converge on exactly one confirmation.
 */
@Service
public class BookingConfirmationService {

    public enum Outcome {
        CONFIRMED,
        ALREADY_CONFIRMED,
        /** The booking had already expired or been cancelled when the money arrived (handled by the late-payment path). */
        NOT_PENDING
    }

    private final BookingRepository bookings;
    private final EventRepository events;
    private final InventoryService inventory;
    private final TicketIssuer issuer;
    private final LedgerService ledger;
    private final InvoiceService invoices;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BookingConfirmationService(BookingRepository bookings, EventRepository events, InventoryService inventory,
            TicketIssuer issuer, LedgerService ledger, InvoiceService invoices, AuditLogService audit, JdbcTemplate jdbc,
            Clock clock) {
        this.bookings = bookings;
        this.events = events;
        this.inventory = inventory;
        this.issuer = issuer;
        this.ledger = ledger;
        this.invoices = invoices;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Confirms a booking whose payment has been verified as captured. Call inside a transaction. */
    public Outcome confirmPaid(UUID bookingId, String razorpayPaymentId) {
        Booking b = bookings.findForUpdate(bookingId).orElseThrow(() -> ApiException.notFound("Booking"));
        return switch (b.getStatus()) {
            case CONFIRMED, PARTIALLY_REFUNDED, REFUNDED -> Outcome.ALREADY_CONFIRMED;
            case EXPIRED, CANCELLED -> Outcome.NOT_PENDING;
            case PENDING_PAYMENT -> {
                confirm(b, Map.of("totalPaise", b.getTotalPaise(), "razorpayPaymentId", razorpayPaymentId));
                yield Outcome.CONFIRMED;
            }
        };
    }

    /** Confirms an expired, locked booking whose tickets the caller has just held again (late payment). */
    void confirmLate(Booking b, String razorpayPaymentId) {
        if (b.getStatus() != BookingStatus.EXPIRED) {
            throw new IllegalStateException("Only an expired booking can be confirmed late");
        }
        b.setCancelReason(null);
        confirm(b, Map.of("totalPaise", b.getTotalPaise(), "razorpayPaymentId", razorpayPaymentId, "late", true));
    }

    /** Confirms a ₹0 booking straight away (no payment). Call inside the transaction that created it. */
    public void confirmFree(Booking b) {
        if (b.getTotalPaise() != 0 || b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only a pending free booking can be confirmed without payment");
        }
        confirm(b, Map.of("totalPaise", 0, "free", true));
    }

    private void confirm(Booking b, Map<String, Object> auditDetails) {
        int quantity = 0;
        for (BookingItem item : b.getItems()) {
            inventory.commit(item.getTicketTypeId(), item.getQuantity());
            quantity += item.getQuantity();
        }
        b.setStatus(BookingStatus.CONFIRMED);
        b.setConfirmedAt(OffsetDateTime.now(clock));
        issuer.issue(b);
        bookings.saveAndFlush(b);

        Event event = events.findById(b.getEventId()).orElseThrow();
        ledger.recordSale(b, event.getOrganizer().getId());
        invoices.issueFor(b);
        jdbc.update("update events set popularity_score = popularity_score + ? where id = ?", quantity, b.getEventId());
        audit.record(b.getUserId(), "BOOKING_CONFIRMED", "booking", b.getId().toString(), null, auditDetails);
    }
}
