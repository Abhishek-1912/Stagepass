package com.stagepass.catalog.api;

import com.stagepass.catalog.service.VenueService;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/venues")
public class OrganizerVenueController {

    private final VenueService venues;
    private final OrganizerGuard guard;
    private final com.stagepass.common.security.CurrentUser currentUser;

    public OrganizerVenueController(VenueService venues, OrganizerGuard guard,
            com.stagepass.common.security.CurrentUser currentUser) {
        this.venues = venues;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<VenueResponse> list() {
        return venues.list(guard.requireApprovedOrganizerId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@Valid @RequestBody VenueRequest request) {
        return venues.create(guard.requireApprovedOrganizerId(), request);
    }

    @PatchMapping("/{id}")
    public VenueResponse update(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        return venues.update(guard.requireApprovedOrganizerId(), currentUser.id(), id, request);
    }
}
