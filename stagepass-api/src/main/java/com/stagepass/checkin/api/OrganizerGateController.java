package com.stagepass.checkin.api;

import com.stagepass.checkin.service.CheckInStats;
import com.stagepass.checkin.service.GateService;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Gates and the scan log of the organizer's own event. */
@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}")
public class OrganizerGateController {

    private final GateService gates;
    private final CheckInStats stats;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerGateController(GateService gates, CheckInStats stats, OrganizerGuard guard, CurrentUser currentUser) {
        this.gates = gates;
        this.stats = stats;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping("/gates")
    public List<GateResponse> list(@PathVariable UUID eventId) {
        return gates.list(guard.requireApprovedOrganizerId(), eventId);
    }

    @PostMapping("/gates")
    public ResponseEntity<GateResponse> create(@PathVariable UUID eventId, @Valid @RequestBody GateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gates.create(guard.requireApprovedOrganizerId(), currentUser.id(), eventId, request));
    }

    @PatchMapping("/gates/{gateId}")
    public GateResponse update(@PathVariable UUID eventId, @PathVariable UUID gateId, @Valid @RequestBody GateRequest request) {
        return gates.update(guard.requireApprovedOrganizerId(), currentUser.id(), eventId, gateId, request);
    }

    @DeleteMapping("/gates/{gateId}")
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID gateId) {
        gates.delete(guard.requireApprovedOrganizerId(), currentUser.id(), eventId, gateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scan-log")
    public PageResponse<ScanLogRow> scanLog(@PathVariable UUID eventId, @RequestParam(required = false) String result,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return stats.scanLog(guard.requireApprovedOrganizerId(), eventId, result, page, size);
    }
}
