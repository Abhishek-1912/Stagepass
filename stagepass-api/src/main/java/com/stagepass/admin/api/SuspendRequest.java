package com.stagepass.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A required reason for a suspension or another admin action that needs one. */
public record SuspendRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
}
