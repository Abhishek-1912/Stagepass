package com.stagepass.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason that is shown to the organizer (rejections, cancellations). */
public record ReviewDecisionRequest(@NotBlank @Size(max = 500) String reason) {

    public ReviewDecisionRequest {
        reason = reason == null ? null : reason.trim();
    }
}
