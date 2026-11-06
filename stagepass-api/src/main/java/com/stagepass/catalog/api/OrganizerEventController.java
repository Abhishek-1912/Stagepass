package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.service.EventService;
import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/events")
public class OrganizerEventController {

    private final EventService events;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerEventController(EventService events, OrganizerGuard guard, CurrentUser currentUser) {
        this.events = events;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventUpsertRequest request) {
        return events.create(guard.requireApprovedOrganizerId(), currentUser.id(), request);
    }

    @GetMapping
    public PageResponse<EventSummaryResponse> list(@RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return events.list(guard.requireApprovedOrganizerId(), status,
                PageRequests.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return events.get(guard.requireApprovedOrganizerId(), id);
    }

    @PatchMapping("/{id}")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventUpsertRequest request) {
        return events.update(guard.requireApprovedOrganizerId(), currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        events.delete(guard.requireApprovedOrganizerId(), currentUser.id(), id);
    }
}
