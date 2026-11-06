package com.stagepass.booking.service;

import com.stagepass.booking.api.CreateBookingRequest;
import com.stagepass.booking.api.QuoteResponse;
import com.stagepass.booking.domain.PriceBreakdown;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.domain.TicketTypeStatus;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.catalog.repository.TicketTypeRepository;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.identity.domain.OrganizerStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everything a quote and a booking must satisfy (spec 8.3), in one place so both agree. Call inside a transaction. */
@Component
public class BookingRules {

    /** One validated line: the ticket type (with its inventory) and the requested quantity. */
    public record Line(TicketType type, int quantity) {

        public long amountPaise() {
            return type.getPricePaise() * quantity;
        }
    }

    public record Checked(Event event, List<Line> lines, PriceBreakdown price) {

        public List<QuoteResponse.Line> quoteLines() {
            return lines.stream().map(l -> new QuoteResponse.Line(l.type().getId(), l.type().getName(), l.quantity(),
                    l.type().getPricePaise(), l.amountPaise())).toList();
        }
    }

    private final EventRepository events;
    private final TicketTypeRepository types;
    private final FeeSettingsRepository fees;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BookingRules(EventRepository events, TicketTypeRepository types, FeeSettingsRepository fees, JdbcTemplate jdbc,
            Clock clock) {
        this.events = events;
        this.types = types;
        this.fees = fees;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Validates the request and prices it. {@code buyerId} null skips the per-user limit (anonymous quote).
     * Availability is checked against current counters; the hold itself is the race-safe check.
     */
    public Checked check(UUID eventId, List<CreateBookingRequest.Item> items, UUID buyerId) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        if (event.getStatus() != EventStatus.PUBLISHED || event.getOrganizer().getStatus() != OrganizerStatus.APPROVED) {
            throw ApiException.notFound("Event");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!event.getStartsAt().isAfter(now)) {
            throw FieldErrors.single("eventId", "this event has already started");
        }

        FieldErrors errors = FieldErrors.start();
        List<Line> lines = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            CreateBookingRequest.Item item = items.get(i);
            String field = "items[" + i + "]";
            if (!seen.add(item.ticketTypeId())) {
                errors.add(field, "this ticket is listed twice");
                continue;
            }
            TicketType type = types.findByIdAndEventId(item.ticketTypeId(), eventId).orElse(null);
            if (type == null || type.getStatus() != TicketTypeStatus.ACTIVE) {
                errors.add(field, "this ticket is not available for this event");
                continue;
            }
            if (type.getSaleStartsAt() != null && now.isBefore(type.getSaleStartsAt())) {
                errors.add(field, "sales for " + type.getName() + " have not started yet");
                continue;
            }
            if (type.getSaleEndsAt() != null && !now.isBefore(type.getSaleEndsAt())) {
                errors.add(field, "sales for " + type.getName() + " have ended");
                continue;
            }
            if (item.quantity() < type.getMinPerOrder() || item.quantity() > type.getMaxPerOrder()) {
                errors.add(field, "choose between " + type.getMinPerOrder() + " and " + type.getMaxPerOrder() + " "
                        + type.getName() + " tickets");
                continue;
            }
            lines.add(new Line(type, item.quantity()));
        }
        errors.throwIfAny();
        // One fixed order for every inventory row lock (holds, releases, commits) so concurrent checkouts can't deadlock.
        lines.sort(Comparator.comparing(l -> l.type().getId().toString())); // same order as Postgres uuid sort

        int requested = lines.stream().mapToInt(Line::quantity).sum();
        if (buyerId != null && event.getMaxTicketsPerUser() != null) {
            int owned = ticketsOwned(buyerId, eventId);
            if (owned + requested > event.getMaxTicketsPerUser()) {
                int left = Math.max(0, event.getMaxTicketsPerUser() - owned);
                throw FieldErrors.single("items", "you can book at most " + event.getMaxTicketsPerUser()
                        + " tickets for this event" + (owned > 0 ? " (" + left + " more)" : ""));
            }
        }
        for (Line l : lines) {
            if (l.type().getInventory().available() < l.quantity()) {
                throw new ApiException(ErrorCode.SOLD_OUT, "Not enough " + l.type().getName() + " tickets left");
            }
        }

        long subtotal = lines.stream().mapToLong(Line::amountPaise).sum();
        PriceBreakdown price = FeeCalculator.price(subtotal, requested, fees.forEvent(eventId));
        return new Checked(event, lines, price);
    }

    /** Tickets the user already has for the event: confirmed bookings in full, partly refunded ones by live tickets. */
    private int ticketsOwned(UUID userId, UUID eventId) {
        Integer n = jdbc.queryForObject("""
                select coalesce(sum(case when b.status = 'CONFIRMED'
                        then (select sum(i.quantity) from booking_items i where i.booking_id = b.id)
                        else (select count(*) from tickets t
                              where t.booking_id = b.id and t.status in ('VALID', 'CHECKED_IN'))
                    end), 0)
                from bookings b
                where b.user_id = ? and b.event_id = ? and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                """, Integer.class, userId, eventId);
        return n == null ? 0 : n;
    }
}
