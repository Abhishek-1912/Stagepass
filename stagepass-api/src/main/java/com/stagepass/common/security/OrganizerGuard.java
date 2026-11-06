package com.stagepass.common.security;

import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.repository.OrganizerRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the caller's organizer id, requiring the ORGANIZER role and an APPROVED organizer profile. */
@Component
public class OrganizerGuard {

    private final CurrentUser currentUser;
    private final OrganizerRepository organizers;

    public OrganizerGuard(CurrentUser currentUser, OrganizerRepository organizers) {
        this.currentUser = currentUser;
        this.organizers = organizers;
    }

    public UUID requireApprovedOrganizerId() {
        if (!currentUser.hasRole(RoleName.ORGANIZER)) {
            throw new ApiException(ErrorCode.ORGANIZER_NOT_APPROVED, "This area is for approved organizers.");
        }
        return organizers.findByUserId(currentUser.id())
                .filter(o -> o.getStatus() == OrganizerStatus.APPROVED)
                .map(o -> o.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORGANIZER_NOT_APPROVED,
                        "Your organizer account isn't approved yet."));
    }
}
