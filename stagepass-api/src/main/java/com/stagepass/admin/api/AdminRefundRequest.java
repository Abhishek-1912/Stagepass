package com.stagepass.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Empty or missing {@code ticketIds} = every live ticket on the booking. */
public record AdminRefundRequest(List<UUID> ticketIds, boolean includeFee,
        @NotBlank @Size(min = 3, max = 500) String reason) {
}
