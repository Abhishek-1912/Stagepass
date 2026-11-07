package com.stagepass.checkin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** A gate: its name and, optionally, the only ticket types allowed through it (empty or missing = all). */
public record GateRequest(@NotBlank @Size(max = 40) String name, @Size(max = 50) List<UUID> ticketTypeIds) {

    public List<UUID> typesOrEmpty() {
        return ticketTypeIds == null ? List.of() : ticketTypeIds.stream().distinct().toList();
    }
}
