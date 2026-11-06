package com.stagepass.booking.api;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Tickets to refund (empty or missing = every valid ticket) and an optional reason. */
public record RefundRequest(@Size(max = 100) List<UUID> ticketIds, @Size(max = 500) String reason) {

    public List<UUID> ticketIdsOrEmpty() {
        return ticketIds == null ? List.of() : ticketIds;
    }
}
