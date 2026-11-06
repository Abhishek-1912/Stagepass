package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.TicketType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One row in the organizer's event list. */
public record EventSummaryResponse(UUID id, String title, String slug, String status, OffsetDateTime startsAt,
        String city, String coverImageUrl, int ticketTypeCount, Long minPricePaise, int totalCapacity, int sold) {

    public static EventSummaryResponse from(Event e) {
        var types = e.getTicketTypes();
        Long minPrice = types.stream().map(TicketType::getPricePaise).min(Long::compare).orElse(null);
        int capacity = types.stream().mapToInt(t -> t.getInventory() == null ? 0 : t.getInventory().getTotal()).sum();
        int sold = types.stream().mapToInt(t -> t.getInventory() == null ? 0 : t.getInventory().getSold()).sum();
        return new EventSummaryResponse(e.getId(), e.getTitle(), e.getSlug(), e.getStatus().name(), e.getStartsAt(),
                e.getVenue() == null ? null : e.getVenue().getCity(), e.getCoverImageUrl(), types.size(), minPrice,
                capacity, sold);
    }
}
