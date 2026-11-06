package com.stagepass.booking.service;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.PriceBreakdown;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.catalog.repository.TicketTypeRepository;
import com.stagepass.common.config.CompanyProperties;
import com.stagepass.common.error.ApiException;
import com.stagepass.payment.repository.PaymentRepository;
import com.stagepass.payment.service.RazorpayClient;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the API view of a booking. Call inside a (read-only) transaction. */
@Component
public class BookingViews {

    private final EventRepository events;
    private final TicketTypeRepository types;
    private final PaymentRepository payments;
    private final RazorpayClient razorpay;
    private final CompanyProperties company;

    public BookingViews(EventRepository events, TicketTypeRepository types, PaymentRepository payments,
            RazorpayClient razorpay, CompanyProperties company) {
        this.events = events;
        this.types = types;
        this.payments = payments;
        this.razorpay = razorpay;
        this.company = company;
    }

    public BookingResponse view(Booking b) {
        Event e = events.findById(b.getEventId()).orElseThrow(() -> ApiException.notFound("Event"));
        Set<UUID> typeIds = new HashSet<>();
        b.getItems().forEach(i -> typeIds.add(i.getTicketTypeId()));
        Map<UUID, String> names = types.findAllById(typeIds).stream()
                .collect(Collectors.toMap(TicketType::getId, TicketType::getName, (a, c) -> a));
        Function<UUID, String> name = id -> names.getOrDefault(id, "Ticket");

        List<BookingResponse.ItemLine> items = b.getItems().stream()
                .map(i -> new BookingResponse.ItemLine(i.getTicketTypeId(), name.apply(i.getTicketTypeId()),
                        i.getQuantity(), i.getUnitPricePaise(), i.getUnitPricePaise() * i.getQuantity()))
                .toList();
        List<BookingResponse.TicketLine> tickets = b.getTickets().stream()
                .map(t -> new BookingResponse.TicketLine(t.getId(), t.getCode(), t.getTicketTypeId(),
                        name.apply(t.getTicketTypeId()), t.getHolderName(), t.getStatus().name()))
                .toList();
        int count = b.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        PriceBreakdown price = new PriceBreakdown(b.getSubtotalPaise(), b.getFeePaise(), b.getFeeGstPaise(),
                b.getTotalPaise(), b.getCommissionPaise(), count);

        BookingResponse.CheckoutInfo checkout = null;
        if (b.getStatus() == BookingStatus.PENDING_PAYMENT && b.getTotalPaise() > 0) {
            checkout = payments.findByBookingId(b.getId())
                    .map(p -> new BookingResponse.CheckoutInfo(razorpay.keyId(), p.getRazorpayOrderId(),
                            p.getAmountPaise(), "INR", company.name()))
                    .orElse(null);
        }
        String venueName = e.getVenue() == null ? null : e.getVenue().getName();
        String city = e.getVenue() == null ? null : e.getVenue().getCity();
        return new BookingResponse(b.getId(), b.getCode(), b.getStatus().name(), e.getId(), e.getTitle(), e.getSlug(),
                e.getStartsAt(), venueName, city, items, price, b.getHoldExpiresAt(), checkout, tickets,
                b.getAttendeeName(), b.getAttendeeEmail(), null, null);
    }
}
