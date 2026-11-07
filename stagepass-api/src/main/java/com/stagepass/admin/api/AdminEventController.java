package com.stagepass.admin.api;

import com.stagepass.catalog.api.AdminEventSummary;
import com.stagepass.catalog.api.EventResponse;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.service.EventLifecycleService;
import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
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
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {

    private final EventLifecycleService lifecycle;
    private final CurrentUser currentUser;

    public AdminEventController(EventLifecycleService lifecycle, CurrentUser currentUser) {
        this.lifecycle = lifecycle;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AdminEventSummary> list(@RequestParam(defaultValue = "PENDING_REVIEW") EventStatus status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Sort sort = status == EventStatus.PENDING_REVIEW ? Sort.by("submittedAt").ascending()
                : Sort.by("startsAt").ascending();
        return lifecycle.adminList(status, q, PageRequests.of(page, size, sort));
    }

    @PostMapping("/{id}/cancel")
    public EventResponse cancel(@PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return lifecycle.adminCancel(currentUser.id(), id, request.reason().strip());
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return lifecycle.adminGet(id);
    }

    @PostMapping("/{id}/approve")
    public EventResponse approve(@PathVariable UUID id) {
        return lifecycle.approve(currentUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public EventResponse reject(@PathVariable UUID id, @Valid @RequestBody ReviewDecisionRequest request) {
        return lifecycle.reject(currentUser.id(), id, request.reason());
    }

    @PostMapping("/{id}/feature")
    public EventResponse feature(@PathVariable UUID id, @Valid @RequestBody FeatureRequest request) {
        return lifecycle.feature(currentUser.id(), id, request.featured());
    }
}
