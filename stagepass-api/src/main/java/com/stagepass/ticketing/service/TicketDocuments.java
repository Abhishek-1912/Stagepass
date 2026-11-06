package com.stagepass.ticketing.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.Invoice;
import com.stagepass.booking.domain.Ticket;
import com.stagepass.booking.domain.TicketStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.repository.InvoiceRepository;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.catalog.repository.TicketTypeRepository;
import com.stagepass.common.config.CompanyProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds a booking's ticket PDF and GST invoice PDF from current data. No ownership checks: callers do that. */
@Service
public class TicketDocuments {

    public record Pdf(String filename, byte[] bytes) {
    }

    private final BookingRepository bookings;
    private final InvoiceRepository invoices;
    private final EventRepository events;
    private final TicketTypeRepository types;
    private final QrTokens qrTokens;
    private final TicketPdfRenderer ticketRenderer;
    private final InvoicePdfRenderer invoiceRenderer;
    private final CompanyProperties company;

    public TicketDocuments(BookingRepository bookings, InvoiceRepository invoices, EventRepository events,
            TicketTypeRepository types, QrTokens qrTokens, TicketPdfRenderer ticketRenderer,
            InvoicePdfRenderer invoiceRenderer, CompanyProperties company) {
        this.bookings = bookings;
        this.invoices = invoices;
        this.events = events;
        this.types = types;
        this.qrTokens = qrTokens;
        this.ticketRenderer = ticketRenderer;
        this.invoiceRenderer = invoiceRenderer;
        this.company = company;
    }

    /** One page per live ticket (valid or checked in); empty when the booking has none. */
    @Transactional(readOnly = true)
    public Optional<Pdf> ticketPdf(UUID bookingId) {
        Booking b = bookings.findById(bookingId).orElse(null);
        if (b == null) {
            return Optional.empty();
        }
        List<Ticket> live = b.getTickets().stream()
                .filter(t -> t.getStatus() == TicketStatus.VALID || t.getStatus() == TicketStatus.CHECKED_IN)
                .sorted(Comparator.comparing(Ticket::getCode)).toList();
        if (live.isEmpty()) {
            return Optional.empty();
        }
        Event e = events.findById(b.getEventId()).orElseThrow();
        Map<UUID, String> names = types.findAllById(live.stream().map(Ticket::getTicketTypeId).distinct().toList())
                .stream().collect(Collectors.toMap(TicketType::getId, TicketType::getName));
        Map<UUID, Long> prices = new HashMap<>();
        for (BookingItem i : b.getItems()) {
            prices.put(i.getTicketTypeId(), i.getUnitPricePaise());
        }
        String venue = e.isOnline() || e.getVenue() == null ? "Online"
                : e.getVenue().getName() + ", " + e.getVenue().getCity();
        List<TicketPdfRenderer.TicketPage> pages = live.stream().map(t -> new TicketPdfRenderer.TicketPage(e.getTitle(),
                e.getStartsAt(), venue, t.getHolderName(), names.getOrDefault(t.getTicketTypeId(), "Ticket"),
                prices.getOrDefault(t.getTicketTypeId(), 0L), t.getCode(), b.getCode(), t.getStatus().name(),
                qrTokens.sign(t.getId(), t.getEventId(), t.getQrNonce()), e.getTerms())).toList();
        return Optional.of(new Pdf("stagepass-" + b.getCode() + ".pdf", ticketRenderer.render(pages)));
    }

    /** The fee invoice, when one was issued (paid booking, company GSTIN configured). */
    @Transactional(readOnly = true)
    public Optional<Pdf> invoicePdf(UUID bookingId) {
        Optional<Invoice> found = invoices.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Invoice inv = found.get();
        Booking b = bookings.findById(bookingId).orElseThrow();
        byte[] bytes = invoiceRenderer.render(new InvoicePdfRenderer.InvoiceDoc(company.name(), company.gstin(),
                company.state(), company.address(), inv.getNumber(), inv.getIssuedAt(), b.getAttendeeName(),
                b.getBuyerState(), b.getCode(), inv.getTaxablePaise(), inv.getCgstPaise(), inv.getSgstPaise(),
                inv.getIgstPaise(), inv.getTotalPaise()));
        return Optional.of(new Pdf("stagepass-invoice-" + inv.getNumber().replace('/', '-') + ".pdf", bytes));
    }
}
