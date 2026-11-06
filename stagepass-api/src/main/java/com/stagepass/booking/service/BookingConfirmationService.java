package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.common.audit.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/** Turns a pending booking into a confirmed one: held tickets become sold and ticket rows are issued. */
@Service
public class BookingConfirmationService {

    private final BookingRepository bookings;
    private final InventoryService inventory;
    private final TicketIssuer issuer;
    private final AuditLogService audit;
    private final Clock clock;

    public BookingConfirmationService(BookingRepository bookings, InventoryService inventory, TicketIssuer issuer,
            AuditLogService audit, Clock clock) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.issuer = issuer;
        this.audit = audit;
        this.clock = clock;
    }

    /** Confirms a ₹0 booking straight away (no payment). Call inside the transaction that created it. */
    public void confirmFree(Booking b) {
        if (b.getTotalPaise() != 0 || b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only a pending free booking can be confirmed without payment");
        }
        for (BookingItem item : b.getItems()) {
            inventory.commit(item.getTicketTypeId(), item.getQuantity());
        }
        issuer.issue(b);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setConfirmedAt(OffsetDateTime.now(clock));
        bookings.saveAndFlush(b);
        audit.record(b.getUserId(), "BOOKING_CONFIRMED", "booking", b.getId().toString(), null,
                Map.of("totalPaise", 0, "free", true));
    }
}
