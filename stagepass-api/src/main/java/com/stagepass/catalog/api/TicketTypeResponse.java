package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.TicketInventory;
import com.stagepass.catalog.domain.TicketType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketTypeResponse(UUID id, String name, String description, long pricePaise, int quantity, int sold,
        int held, int available, OffsetDateTime saleStartsAt, OffsetDateTime saleEndsAt, int minPerOrder,
        int maxPerOrder, int sortOrder, String status) {

    public static TicketTypeResponse from(TicketType t) {
        TicketInventory inv = t.getInventory();
        int total = inv == null ? 0 : inv.getTotal();
        int sold = inv == null ? 0 : inv.getSold();
        int held = inv == null ? 0 : inv.getHeld();
        return new TicketTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getPricePaise(), total, sold, held,
                Math.max(0, total - sold - held), t.getSaleStartsAt(), t.getSaleEndsAt(), t.getMinPerOrder(),
                t.getMaxPerOrder(), t.getSortOrder(), t.getStatus().name());
    }
}
