package com.stagepass.catalog.api;

import com.stagepass.admin.api.ReviewDecisionRequest;
import com.stagepass.catalog.service.EventLifecycleService;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/events/{id}")
public class OrganizerEventLifecycleController {

    private final EventLifecycleService lifecycle;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerEventLifecycleController(EventLifecycleService lifecycle, OrganizerGuard guard,
            CurrentUser currentUser) {
        this.lifecycle = lifecycle;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @PostMapping("/submit")
    public EventResponse submit(@PathVariable UUID id) {
        return lifecycle.submit(guard.requireApprovedOrganizerId(), currentUser.id(), id);
    }

    @PostMapping("/cancel")
    public EventResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReviewDecisionRequest request) {
        return lifecycle.cancel(guard.requireApprovedOrganizerId(), currentUser.id(), id, request.reason());
    }
}
