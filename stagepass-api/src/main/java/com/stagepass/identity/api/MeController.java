package com.stagepass.identity.api;

import com.stagepass.common.security.CurrentUser;
import com.stagepass.identity.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUser currentUser;
    private final ProfileService profiles;

    public MeController(CurrentUser currentUser, ProfileService profiles) {
        this.currentUser = currentUser;
        this.profiles = profiles;
    }

    @GetMapping
    public MeResponse me() {
        return MeResponse.from(profiles.get(currentUser.id()));
    }

    @PatchMapping
    public MeResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return MeResponse.from(profiles.update(currentUser.id(), request));
    }
}
