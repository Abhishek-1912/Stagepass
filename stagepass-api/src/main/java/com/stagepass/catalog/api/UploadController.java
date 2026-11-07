package com.stagepass.catalog.api;

import com.stagepass.catalog.service.UploadService;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UploadController {

    private final UploadService uploads;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public UploadController(UploadService uploads, OrganizerGuard guard, CurrentUser currentUser) {
        this.uploads = uploads;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    /** Event images: approved organizers, own events only. */
    @PostMapping("/api/v1/organizer/uploads/signature")
    public UploadSignatureResponse event(@Valid @RequestBody UploadSignatureRequest request) {
        if (request.purpose() != UploadSignatureRequest.Purpose.EVENT_IMAGE) {
            throw FieldErrors.single("purpose", "use /api/v1/uploads/signature for logos");
        }
        return uploads.signEventImage(guard.requireApprovedOrganizerId(), request.eventId());
    }

    /** Organizer logo (needed while applying) or profile photo: any signed-in user, own folder only. */
    @PostMapping("/api/v1/uploads/signature")
    public UploadSignatureResponse logo(@Valid @RequestBody UploadSignatureRequest request) {
        if (request.purpose() == UploadSignatureRequest.Purpose.AVATAR) {
            return uploads.signAvatar(currentUser.id());
        }
        if (request.purpose() != UploadSignatureRequest.Purpose.ORGANIZER_LOGO) {
            throw FieldErrors.single("purpose", "only ORGANIZER_LOGO or AVATAR is allowed here");
        }
        return uploads.signLogo(currentUser.id());
    }
}
