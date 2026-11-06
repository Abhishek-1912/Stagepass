package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.common.audit.AuditLogService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** Ends a pending booking and gives its held tickets back. Call inside a transaction. */
@Service
public class BookingExpiryService {

    private final BookingRepository bookings;
    private final InventoryService inventory;
    private final AuditLogService audit;

    public BookingExpiryService(BookingRepository bookings, InventoryService inventory, AuditLogService audit) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.audit = audit;
    }

    /** Locks the booking and expires it if it is still pending; returns whether it did. */
    public boolean expire(UUID bookingId, String reason) {
        Booking b = bookings.findForUpdate(bookingId).orElse(null);
        if (b == null || b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        for (BookingItem item : b.getItems()) {
            inventory.release(item.getTicketTypeId(), item.getQuantity());
        }
        b.setStatus(BookingStatus.EXPIRED);
        b.setCancelReason(reason);
        bookings.saveAndFlush(b);
        audit.record(null, "BOOKING_EXPIRED", "booking", b.getId().toString(), null, Map.of("reason", reason));
        return true;
    }
}
