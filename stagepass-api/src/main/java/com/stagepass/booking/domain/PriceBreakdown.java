package com.stagepass.booking.domain;

/** All amounts in paise. {@code total = subtotal + fee + feeGst}; commission is the organizer's charge, not the buyer's. */
public record PriceBreakdown(long subtotalPaise, long feePaise, long feeGstPaise, long totalPaise, long commissionPaise,
        int ticketCount) {
}
