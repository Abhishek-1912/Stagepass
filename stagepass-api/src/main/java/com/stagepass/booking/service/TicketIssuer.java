package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.Ticket;
import com.stagepass.booking.repository.TicketRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Creates one ticket row per admission when a booking is confirmed. Call inside the confirming transaction. */
@Component
public class TicketIssuer {

    private final TicketRepository tickets;

    public TicketIssuer(TicketRepository tickets) {
        this.tickets = tickets;
    }

    public void issue(Booking booking) {
        if (!booking.getTickets().isEmpty()) {
            return;
        }
        Set<String> used = new HashSet<>();
        for (BookingItem item : booking.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                Ticket t = new Ticket();
                t.setBooking(booking);
                t.setBookingItemId(item.getId());
                t.setTicketTypeId(item.getTicketTypeId());
                t.setEventId(booking.getEventId());
                t.setCode(uniqueCode(used));
                t.setHolderName(booking.getAttendeeName());
                t.setQrNonce(BookingCodes.nonce());
                booking.getTickets().add(t);
            }
        }
    }

    private String uniqueCode(Set<String> used) {
        while (true) {
            String code = BookingCodes.ticket();
            if (used.add(code) && !tickets.existsByCode(code)) {
                return code;
            }
        }
    }
}
