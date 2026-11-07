package com.stagepass.checkin.api;

import java.util.List;
import java.util.UUID;

public record GateResponse(UUID id, String name, List<UUID> ticketTypeIds, int checkedIn) {
}
