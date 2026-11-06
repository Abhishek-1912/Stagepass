package com.stagepass.discovery.api;

import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.domain.TicketTypeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A ticket type as attendees see it. Exact remaining counts are deliberately not exposed. */
public record PublicTicketType(UUID id, String name, String description, long pricePaise, String availability,
        OffsetDateTime saleStartsAt, OffsetDateTime saleEndsAt, int minPerOrder, int maxPerOrder, boolean fewLeft) {

    public static PublicTicketType from(TicketType t, OffsetDateTime now) {
        int total = t.getInventory().getTotal();
        int available = t.getInventory().available();
        String availability;
        if (t.getSaleEndsAt() != null && !t.getSaleEndsAt().isAfter(now)) {
            availability = "ENDED";
        } else if (t.getSaleStartsAt() != null && t.getSaleStartsAt().isAfter(now)) {
            availability = "NOT_STARTED";
        } else if (available <= 0 || t.getStatus() == TicketTypeStatus.SOLD_OUT) {
            availability = "SOLD_OUT";
        } else {
            availability = "ON_SALE";
        }
        boolean fewLeft = "ON_SALE".equals(availability) && (available <= 10 || available * 10 <= total);
        return new PublicTicketType(t.getId(), t.getName(), t.getDescription(), t.getPricePaise(), availability,
                t.getSaleStartsAt(), t.getSaleEndsAt(), t.getMinPerOrder(), t.getMaxPerOrder(), fewLeft);
    }
}
