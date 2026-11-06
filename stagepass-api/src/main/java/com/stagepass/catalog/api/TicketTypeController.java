package com.stagepass.catalog.api;

import com.stagepass.catalog.service.TicketTypeService;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/ticket-types")
public class TicketTypeController {

    private final TicketTypeService types;
    private final OrganizerGuard guard;

    public TicketTypeController(TicketTypeService types, OrganizerGuard guard) {
        this.types = types;
        this.guard = guard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse create(@PathVariable UUID eventId, @Valid @RequestBody TicketTypeRequest request) {
        return types.create(guard.requireApprovedOrganizerId(), eventId, request);
    }

    @PatchMapping("/{ticketTypeId}")
    public TicketTypeResponse update(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId,
            @Valid @RequestBody TicketTypeRequest request) {
        return types.update(guard.requireApprovedOrganizerId(), eventId, ticketTypeId, request);
    }

    @DeleteMapping("/{ticketTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId, @PathVariable UUID ticketTypeId) {
        types.delete(guard.requireApprovedOrganizerId(), eventId, ticketTypeId);
    }
}
