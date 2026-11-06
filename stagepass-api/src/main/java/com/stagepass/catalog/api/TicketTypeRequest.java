package com.stagepass.catalog.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Create (name, pricePaise, quantity required — checked in the service) or partial update of a ticket type. */
public record TicketTypeRequest(
        @Size(min = 1, max = 80) String name,
        @Size(max = 500) String description,
        @Min(0) @Max(1_000_000_000L) Long pricePaise,
        @Min(1) @Max(100_000) Integer quantity,
        OffsetDateTime saleStartsAt,
        OffsetDateTime saleEndsAt,
        @Min(1) @Max(50) Integer minPerOrder,
        @Min(1) @Max(50) Integer maxPerOrder,
        @Min(0) @Max(1000) Integer sortOrder,
        String status) {

    public TicketTypeRequest {
        name = Formats.trim(name);
        description = Formats.trim(description);
        status = Formats.upper(status);
    }
}
