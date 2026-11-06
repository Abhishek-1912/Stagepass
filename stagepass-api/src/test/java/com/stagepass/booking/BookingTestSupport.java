package com.stagepass.booking;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.api.CreateBookingRequest;
import com.stagepass.booking.service.BookingService;
import com.stagepass.payment.service.PaymentService;
import org.springframework.context.annotation.Lazy;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.SharedRazorpay;
import com.stagepass.support.TestUsers;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Builds request bodies and live events for booking tests. */
@Component
public class BookingTestSupport {

    private final CatalogFixtures catalog;
    private final TestUsers users;
    private final BookingService bookings;
    private final PaymentService payments;

    public BookingTestSupport(CatalogFixtures catalog, TestUsers users, BookingService bookings,
            @Lazy PaymentService payments) {
        this.catalog = catalog;
        this.users = users;
        this.bookings = bookings;
        this.payments = payments;
    }

    public record LiveEvent(UUID eventId, UUID ticketTypeId, TestUsers.TestUser organizer) {
    }

    /** A pending (or, for ₹0, confirmed) booking of {@code qty} tickets made through the real service. */
    public BookingResponse book(LiveEvent e, int qty, TestUsers.TestUser buyer) {
        return bookings.create(buyer.id(), "test-" + UUID.randomUUID(), new CreateBookingRequest(e.eventId(),
                List.of(new CreateBookingRequest.Item(e.ticketTypeId(), qty)), "Asha Rao", "asha@test.in", null,
                "Telangana")).booking();
    }

    /** A paid booking confirmed through the real settle path (as the verify call or webhook would). */
    public BookingResponse confirmed(LiveEvent e, int qty, TestUsers.TestUser buyer) {
        BookingResponse pending = book(e, qty, buyer);
        if (pending.checkout() == null) {
            return pending;
        }
        payments.settle(capturedPayment(pending));
        return bookings.detail(buyer.id(), pending.id());
    }

    /** Scripts a captured Razorpay payment for the booking's order and returns its id. */
    public static String capturedPayment(BookingResponse b) {
        return scriptedPayment(b, b.checkout().amountPaise(), "captured");
    }

    public static String scriptedPayment(BookingResponse b, long amountPaise, String status) {
        String id = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        SharedRazorpay.server().payment(id, b.checkout().razorpayOrderId(), amountPaise, status);
        return id;
    }

    /** A published event 10 days out with one extra ACTIVE type at {@code pricePaise} × {@code quantity}. */
    public LiveEvent liveEvent(long pricePaise, int quantity) {
        TestUsers.TestUser org = users.approvedOrganizer();
        UUID event = catalog.publishedEvent(org, "Booking test", OffsetDateTime.now().plusDays(10), "Bookcity", 100);
        UUID type = catalog.ticketType(event, pricePaise, quantity);
        return new LiveEvent(event, type, org);
    }

    public static String items(UUID eventId, UUID typeId, int qty) {
        return "{\"eventId\":\"%s\",\"items\":[{\"ticketTypeId\":\"%s\",\"quantity\":%d}]}".formatted(eventId, typeId, qty);
    }

    public static String booking(UUID eventId, UUID typeId, int qty) {
        return """
                {"eventId":"%s","items":[{"ticketTypeId":"%s","quantity":%d}],
                 "attendeeName":"Asha Rao","attendeeEmail":"asha@test.in","attendeePhone":"9876543210","buyerState":"Telangana"}
                """.formatted(eventId, typeId, qty);
    }
}
