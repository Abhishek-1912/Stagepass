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

    /**
     * What the attendee can do now and what already happened. {@code selfServe}: a request refunds immediately;
     * {@code requestable}: a request can be sent at all (to the organizer when not self-serve).
     */
    public record RefundInfo(boolean selfServe, boolean requestable, String kind, boolean includesFee,
            OffsetDateTime until, String message, List<RefundLine> history) {
    }

    public record RefundLine(UUID id, String status, String kind, long amountPaise, int ticketCount,
            List<UUID> ticketIds, String note, OffsetDateTime createdAt) {
    }
}
