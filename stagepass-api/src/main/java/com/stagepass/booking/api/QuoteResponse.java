package com.stagepass.booking.api;

import com.stagepass.booking.domain.PriceBreakdown;

import java.util.List;
import java.util.UUID;

public record QuoteResponse(List<Line> lines, PriceBreakdown price, int holdMinutes) {

    public record Line(UUID ticketTypeId, String name, int quantity, long unitPricePaise, long amountPaise) {
    }
}
