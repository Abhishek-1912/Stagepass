package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.Refund;
import com.stagepass.booking.domain.Ticket;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.catalog.repository.TicketTypeRepository;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import com.stagepass.notification.service.NotificationService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Queues the attendee emails of the booking lifecycle (spec 8.9). Call inside the business transaction: the email
 * commits with the change or not at all, and each dedupe key makes repeated triggers (webhook retries, sweeps) send
 * one email.
 */
@Component
public class BookingEmails {

    private final EmailOutbox outbox;
    private final EventRepository events;
    private final BookingRepository bookings;
    private final TicketTypeRepository types;
    private final NotificationService notifications;

    public BookingEmails(EmailOutbox outbox, EventRepository events, BookingRepository bookings, TicketTypeRepository types,
            NotificationService notifications) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.events = events;
        this.bookings = bookings;
        this.types = types;
    }

    /** Booking confirmed (paid, free or late); the ticket PDF is attached at send time. */
    public void confirmed(Booking b, String invoiceNumber) {
        Map<UUID, String> names = types.findAllById(b.getItems().stream().map(i -> i.getTicketTypeId()).distinct().toList())
                .stream().collect(Collectors.toMap(TicketType::getId, TicketType::getName));
        List<Map<String, String>> tickets = b.getTickets().stream().sorted(Comparator.comparing(Ticket::getCode))
                .map(t -> Map.of("code", t.getCode(), "type", names.getOrDefault(t.getTicketTypeId(), "Ticket")))
                .toList();
        Map<String, Object> data = base(b);
        data.put("tickets", tickets);
        data.put("totalPaise", b.getTotalPaise());
        if (invoiceNumber != null) {
            data.put("invoiceNumber", invoiceNumber);
        }
        send(b, EmailTemplate.BOOKING_CONFIRMED, data, "booking-confirmed:" + b.getId());
        inApp(b, "BOOKING_CONFIRMED", "Booking confirmed: " + data.get("title"),
                b.getTickets().size() + (b.getTickets().size() == 1 ? " ticket" : " tickets") + " ready in My bookings.",
                "booking-confirmed:" + b.getId());
    }

    /** The hold ran out before payment (not for replaced checkouts or gateway failures the buyer already saw). */
    public void expired(Booking b) {
        send(b, EmailTemplate.BOOKING_EXPIRED, base(b), "booking-expired:" + b.getId());
    }

    public void refundProcessed(Refund r) {
        Booking b = bookings.findById(r.getBookingId()).orElseThrow();
        Map<String, Object> data = base(b);
        data.put("amountPaise", r.getAmountPaise());
        data.put("includesFee", r.getFeePaise() + r.getFeeGstPaise() > 0);
        int count = r.getTicketIds().length;
        data.put("ticketCount", count > 0 ? count : b.getItems().stream().mapToInt(i -> i.getQuantity()).sum());
        send(b, EmailTemplate.REFUND_PROCESSED, data, "refund-processed:" + r.getId());
        inApp(b, "REFUND_PROCESSED", "Refund of " + rupees(r.getAmountPaise()) + " processed",
                "For " + data.get("title") + ". It usually reaches your account in 5-7 working days.",
                "refund-processed:" + r.getId());
    }

    public void refundDeclined(Refund r, String note) {
        Booking b = bookings.findById(r.getBookingId()).orElseThrow();
        Map<String, Object> data = base(b);
        if (note != null && !note.isBlank()) {
            data.put("note", note.strip());
        }
        send(b, EmailTemplate.REFUND_DECLINED, data, "refund-declined:" + r.getId());
        inApp(b, "REFUND_DECLINED", "Refund request declined",
                "For " + data.get("title") + (note != null && !note.isBlank() ? ": " + note.strip() : "."),
                "refund-declined:" + r.getId());
    }

    /** The event was cancelled: {@code refundedPaise} is what goes back (0 for an open checkout). */
    public void eventCancelled(Booking b, long refundedPaise) {
        Map<String, Object> data = base(b);
        data.put("refunded", refundedPaise > 0);
        data.put("amountPaise", refundedPaise);
        send(b, EmailTemplate.EVENT_CANCELLED, data, "event-cancelled:" + b.getId());
        inApp(b, "EVENT_CANCELLED", data.get("title") + " was cancelled",
                refundedPaise > 0 ? "Your refund of " + rupees(refundedPaise) + " is on its way." : "Sorry - the organizer cancelled it.",
                "event-cancelled:" + b.getId());
    }

    /** Date, time or venue changed: tell every ticket holder, with the end of the full-refund window. */
    public void eventChanged(Event e) {
        if (e.getRescheduleRefundUntil() == null) {
            return;
        }
        long window = e.getRescheduleRefundUntil().toInstant().toEpochMilli();
        for (Booking b : bookings.findByEventIdAndStatusIn(e.getId(),
                List.of(BookingStatus.CONFIRMED, BookingStatus.PARTIALLY_REFUNDED))) {
            Map<String, Object> data = base(b, e);
            data.put("refundUntil", e.getRescheduleRefundUntil().toString());
            send(b, EmailTemplate.EVENT_CHANGED, data, "event-changed:" + b.getId() + ":" + window);
            inApp(b, "EVENT_CHANGED", e.getTitle() + " has a new date or venue",
                    "Check the new details. You can get a full refund until the change window ends.",
                    "event-changed:" + b.getId() + ":" + window);
        }
    }

    private Map<String, Object> base(Booking b) {
        return base(b, events.findById(b.getEventId()).orElseThrow());
    }

    private static Map<String, Object> base(Booking b, Event e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", b.getId().toString());
        m.put("bookingCode", b.getCode());
        m.put("title", e.getTitle());
        m.put("slug", e.getSlug());
        m.put("startsAt", e.getStartsAt().toString());
        m.put("venue", e.isOnline() || e.getVenue() == null ? "Online" : e.getVenue().getName() + ", " + e.getVenue().getCity());
        m.put("attendeeName", b.getAttendeeName());
        return m;
    }

    private void inApp(Booking b, String type, String title, String body, String dedupeKey) {
        notifications.notify(b.getUserId(), type, title, body, "/account/bookings/" + b.getId(), dedupeKey);
    }

    private static String rupees(long paise) {
        return "Rs. " + java.math.BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private void send(Booking b, EmailTemplate template, Map<String, Object> data, String dedupeKey) {
        outbox.enqueue(b.getAttendeeEmail(), template, data, dedupeKey);
    }
}
