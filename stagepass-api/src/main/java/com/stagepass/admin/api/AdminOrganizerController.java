package com.stagepass.admin.api;

import com.stagepass.admin.service.AdminOrganizerService;
import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.identity.api.OrganizerApplicationResponse;
import com.stagepass.identity.domain.OrganizerStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/organizer-applications")
public class AdminOrganizerController {

    private final AdminOrganizerService service;
    private final CurrentUser currentUser;

    public AdminOrganizerController(AdminOrganizerService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<OrganizerApplicationResponse> list(
            @RequestParam(defaultValue = "PENDING") OrganizerStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(status, PageRequests.of(page, size, Sort.by("appliedAt").ascending()));
    }

    @PostMapping("/{id}/approve")
    public OrganizerApplicationResponse approve(@PathVariable UUID id) {
        return service.approve(currentUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public OrganizerApplicationResponse reject(@PathVariable UUID id, @Valid @RequestBody ReviewDecisionRequest request) {
        return service.reject(currentUser.id(), id, request.reason());
    }
}
