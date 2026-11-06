package com.stagepass.catalog.service;

import com.stagepass.catalog.api.TicketTypeRequest;
import com.stagepass.catalog.api.TicketTypeResponse;
import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.TicketInventory;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.domain.TicketTypeStatus;
import com.stagepass.catalog.repository.TicketInventoryRepository;
import com.stagepass.catalog.repository.TicketTypeRepository;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ticket types and their inventory. Quantity changes go through a conditional SQL update so they cannot race a
 * booking that is holding or selling tickets at the same moment.
 */
@Service
public class TicketTypeService {

    static final int MAX_TYPES_PER_EVENT = 20;

    private final EventService events;
    private final TicketTypeRepository types;
    private final TicketInventoryRepository inventory;

    public TicketTypeService(EventService events, TicketTypeRepository types, TicketInventoryRepository inventory) {
        this.events = events;
        this.types = types;
        this.inventory = inventory;
    }

    @Transactional
    public TicketTypeResponse create(UUID organizerId, UUID eventId, TicketTypeRequest r) {
        Event event = editableEvent(organizerId, eventId);
        FieldErrors.start().require(r.name(), "name").require(r.pricePaise(), "pricePaise")
                .require(r.quantity(), "quantity").throwIfAny();
        if (types.countByEventId(eventId) >= MAX_TYPES_PER_EVENT) {
            throw ApiException.conflict("An event can have at most " + MAX_TYPES_PER_EVENT + " ticket types.");
        }
        TicketType type = TicketType.create(event, r.name(), r.pricePaise(), r.quantity());
        applyDetails(type, event, r);
        event.getTicketTypes().add(type);
        types.saveAndFlush(type);
        return TicketTypeResponse.from(type);
    }

    @Transactional
    public TicketTypeResponse update(UUID organizerId, UUID eventId, UUID typeId, TicketTypeRequest r) {
        Event event = editableEvent(organizerId, eventId);
        TicketType type = owned(eventId, typeId);
        TicketInventory inv = type.getInventory();

        if (r.pricePaise() != null && r.pricePaise() != type.getPricePaise()) {
            if (inv.getSold() > 0) {
                throw ApiException.conflict("Tickets of this type have been sold, so its price is locked. "
                        + "Create a new ticket type to change the price.");
            }
            type.setPricePaise(r.pricePaise());
        }
        if (r.name() != null) {
            type.setName(r.name());
        }
        applyDetails(type, event, r);
        types.flush();

        if (r.quantity() != null && r.quantity() != inv.getTotal()) {
            if (inventory.resize(typeId, r.quantity()) == 0) {
                TicketInventory fresh = inventory.findById(typeId).orElseThrow();
                throw ApiException.conflict("You can't go below " + (fresh.getSold() + fresh.getHeld())
                        + " tickets — that many are already sold or reserved.");
            }
            // resize() clears the persistence context; reload to report the new numbers.
            return TicketTypeResponse.from(types.findById(typeId).orElseThrow());
        }
        return TicketTypeResponse.from(type);
    }

    @Transactional
    public void delete(UUID organizerId, UUID eventId, UUID typeId) {
        Event event = editableEvent(organizerId, eventId);
        TicketType type = owned(eventId, typeId);
        if (type.getInventory().getSold() > 0 || type.getInventory().getHeld() > 0) {
            throw ApiException.conflict("Tickets of this type have been sold or reserved. Hide it instead.");
        }
        event.getTicketTypes().remove(type);
        types.delete(type);
    }

    private Event editableEvent(UUID organizerId, UUID eventId) {
        Event event = events.owned(organizerId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Cancelled or completed events can't be changed.");
        }
        return event;
    }

    private TicketType owned(UUID eventId, UUID typeId) {
        return types.findByIdAndEventId(typeId, eventId).orElseThrow(() -> ApiException.notFound("Ticket type"));
    }

    private static void applyDetails(TicketType t, Event event, TicketTypeRequest r) {
        FieldErrors errors = FieldErrors.start();
        if (r.description() != null) {
            t.setDescription(r.description());
        }
        if (r.saleStartsAt() != null) {
            t.setSaleStartsAt(r.saleStartsAt());
        }
        if (r.saleEndsAt() != null) {
            t.setSaleEndsAt(r.saleEndsAt());
        }
        if (t.getSaleEndsAt() != null && t.getSaleEndsAt().isAfter(event.getEndsAt())) {
            errors.add("saleEndsAt", "Sales must end before the event ends");
        }
        if (t.getSaleStartsAt() != null && t.getSaleEndsAt() != null && !t.getSaleEndsAt().isAfter(t.getSaleStartsAt())) {
            errors.add("saleEndsAt", "Sales must end after they start");
        }
        if (r.minPerOrder() != null) {
            t.setMinPerOrder(r.minPerOrder());
        }
        if (r.maxPerOrder() != null) {
            t.setMaxPerOrder(r.maxPerOrder());
        }
        if (t.getMaxPerOrder() < t.getMinPerOrder()) {
            errors.add("maxPerOrder", "Maximum per order must be at least the minimum");
        }
        if (r.sortOrder() != null) {
            t.setSortOrder(r.sortOrder());
        }
        if (r.status() != null) {
            try {
                t.setStatus(TicketTypeStatus.valueOf(r.status()));
            } catch (IllegalArgumentException e) {
                errors.add("status", "must be ACTIVE, HIDDEN or SOLD_OUT");
            }
        }
        errors.throwIfAny();
    }
}
