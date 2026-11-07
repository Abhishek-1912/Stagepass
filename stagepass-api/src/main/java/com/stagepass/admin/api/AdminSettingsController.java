package com.stagepass.admin.api;

import com.stagepass.admin.service.PlatformSettingsService;
import com.stagepass.booking.service.FeeSettings;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    private final PlatformSettingsService service;
    private final CurrentUser currentUser;

    public AdminSettingsController(PlatformSettingsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/fees")
    public FeeSettings fees() {
        return service.fees();
    }

    @PutMapping("/fees")
    public FeeSettings updateFees(@Valid @RequestBody FeeSettingsRequest request) {
        return service.updateFees(currentUser.id(), request);
    }
}
