package com.stagepass.admin.api;

import com.stagepass.admin.service.AdminUserService;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AdminUserRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String role, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(q, role, status, page, size);
    }

    @PostMapping("/{id}/suspend")
    public AdminUserRow suspend(@PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return service.suspend(currentUser.id(), id, request.reason());
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserRow reactivate(@PathVariable UUID id) {
        return service.reactivate(currentUser.id(), id);
    }

    @PutMapping("/{id}/roles/{role}")
    public AdminUserRow grant(@PathVariable UUID id, @PathVariable String role) {
        AdminUserService.requireAdminRole(role);
        return service.grantAdmin(currentUser.id(), id);
    }

    @DeleteMapping("/{id}/roles/{role}")
    public AdminUserRow revoke(@PathVariable UUID id, @PathVariable String role) {
        AdminUserService.requireAdminRole(role);
        return service.revokeAdmin(currentUser.id(), id);
    }
}
