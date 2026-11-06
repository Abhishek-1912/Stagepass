package com.stagepass.organizer.api;

import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import com.stagepass.organizer.service.OrganizationSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer/profile")
public class OrganizationSettingsController {

    private final OrganizationSettingsService settings;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizationSettingsController(OrganizationSettingsService settings, OrganizerGuard guard, CurrentUser currentUser) {
        this.settings = settings;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping
    public OrganizationSettingsResponse get() {
        return settings.get(guard.requireApprovedOrganizerId());
    }

    @PatchMapping
    public OrganizationSettingsResponse update(@Valid @RequestBody OrganizationSettingsRequest request) {
        return settings.update(guard.requireApprovedOrganizerId(), currentUser.id(), request);
    }
}
