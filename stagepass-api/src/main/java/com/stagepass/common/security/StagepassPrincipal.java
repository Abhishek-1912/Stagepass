package com.stagepass.common.security;

import com.stagepass.identity.domain.RoleName;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller, as resolved from a Supabase token plus our own role data. */
public record StagepassPrincipal(UUID userId, String email, Set<RoleName> roles) {

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }
}
