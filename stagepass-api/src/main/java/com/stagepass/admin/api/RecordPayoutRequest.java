package com.stagepass.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A bank transfer already made to an organizer; {@code reference} is the UTR. */
public record RecordPayoutRequest(@NotNull @Positive Long amountPaise, @NotBlank @Size(min = 3, max = 100) String reference,
        @NotNull LocalDate paidOn, @Size(max = 500) String note) {
}
