package com.stagepass.identity.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** Partial profile update: null fields are left unchanged. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 120) String fullName,
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a 10-digit Indian mobile number") String phone,
        @Size(max = 80) String city,
        @URL @Size(max = 500) String avatarUrl,
        Boolean emailReminders) {
}
