package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.LedgerEntry;
import com.stagepass.booking.domain.LedgerType;
import com.stagepass.booking.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Organizer ledger postings in signed paise (positive = owed to the organizer). Call inside a transaction. */
@Service
public class LedgerService {

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /** On confirmation: the ticket subtotal is the organizer's, minus Stagepass commission. */
    public void recordSale(Booking b, UUID organizerId) {
        if (b.getSubtotalPaise() == 0) {
            return;
        }
        post(organizerId, b, LedgerType.SALE, b.getSubtotalPaise(), "Booking " + b.getCode());
        if (b.getCommissionPaise() > 0) {
            post(organizerId, b, LedgerType.COMMISSION, -b.getCommissionPaise(), "Commission on " + b.getCode());
        }
    }

    /** On refund: the ticket portion comes back off the organizer's balance; the commission on it is returned. */
    public void recordRefund(Booking b, UUID organizerId, long ticketPortionPaise, long commissionPortionPaise) {
        if (ticketPortionPaise > 0) {
            post(organizerId, b, LedgerType.REFUND, -ticketPortionPaise, "Refund on " + b.getCode());
        }
        if (commissionPortionPaise > 0) {
            post(organizerId, b, LedgerType.COMMISSION, commissionPortionPaise, "Commission returned on " + b.getCode());
        }
    }

    private void post(UUID organizerId, Booking b, LedgerType type, long amountPaise, String note) {
        LedgerEntry e = new LedgerEntry();
        e.setOrganizerId(organizerId);
        e.setEventId(b.getEventId());
        e.setBookingId(b.getId());
        e.setType(type);
        e.setAmountPaise(amountPaise);
        e.setNote(note);
        entries.save(e);
    }
}
