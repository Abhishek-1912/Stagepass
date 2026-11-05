package com.stagepass.identity.api;

import com.stagepass.identity.domain.User;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record MeResponse(UUID id, String email, String fullName, String phone, String avatarUrl, String city,
        Set<String> roles) {

    public static MeResponse from(User user) {
        Set<String> roles = new TreeSet<>();
        user.roleNames().forEach(r -> roles.add(r.name()));
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getAvatarUrl(), user.getCity(), roles);
    }
}
