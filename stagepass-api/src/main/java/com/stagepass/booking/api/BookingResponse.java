package com.stagepass.booking.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stagepass.booking.domain.PriceBreakdown;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(UUID id, String code, String status, UUID eventId, String eventTitle, String eventSlug,
        OffsetDateTime eventStartsAt, String venueName, String city, List<ItemLine> items, PriceBreakdown price,
        OffsetDateTime holdExpiresAt, CheckoutInfo checkout, List<TicketLine> tickets, String attendeeName,
        String attendeeEmail, String invoiceNumber, RefundInfo refund) {

    public record ItemLine(UUID ticketTypeId, String name, int quantity, long unitPricePaise, long amountPaise) {
    }

    public record CheckoutInfo(String razorpayKeyId, String razorpayOrderId, long amountPaise, String currency,
            String companyName) {
    }

    public record TicketLine(UUID id, String code, UUID ticketTypeId, String ticketTypeName, String holderName,
            String status) {
    }

    /** Latest refund on the booking; filled in by the refund flow. */
    public record RefundInfo(UUID id, String status, String kind, long amountPaise, OffsetDateTime createdAt) {
    }
}
