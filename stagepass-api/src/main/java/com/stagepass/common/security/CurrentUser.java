package com.stagepass.common.security;

import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.domain.RoleName;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Access to the authenticated caller from services and controllers. */
@Component
public class CurrentUser {

    public StagepassPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof StagepassPrincipal principal) {
            return principal;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }

    public UUID id() {
        return get().userId();
    }

    public boolean hasRole(RoleName role) {
        return get().hasRole(role);
    }
}
