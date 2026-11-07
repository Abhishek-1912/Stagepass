package com.stagepass.admin.api;

import com.stagepass.admin.service.AdminOrganizerManagement;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/** Approved and suspended organizers (applications stay on {@link AdminOrganizerController}). */
@RestController
@RequestMapping("/api/v1/admin/organizers")
public class AdminOrganizersController {

    private final AdminOrganizerManagement service;
    private final CurrentUser currentUser;

    public AdminOrganizersController(AdminOrganizerManagement service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AdminOrganizerRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminOrganizerDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/suspend")
    public AdminOrganizerDetail suspend(@PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return service.suspend(currentUser.id(), id, request.reason());
    }

    @PostMapping("/{id}/reinstate")
    public AdminOrganizerDetail reinstate(@PathVariable UUID id) {
        return service.reinstate(currentUser.id(), id);
    }

    /** Partial update; see {@link AdminOrganizerManagement#update}. */
    @PatchMapping("/{id}")
    public AdminOrganizerDetail update(@PathVariable UUID id, @RequestBody JsonNode body) {
        return service.update(currentUser.id(), id, body);
    }
}
