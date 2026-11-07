package com.stagepass.checkin.api;

import com.stagepass.checkin.service.CheckInService;
import com.stagepass.checkin.service.CheckInStats;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The door scanner: open to any signed-in user, access decided per event (organizer or door staff). */
@RestController
public class CheckInController {

    private final CheckInService checkIn;
    private final CheckInStats stats;
    private final CurrentUser currentUser;

    public CheckInController(CheckInService checkIn, CheckInStats stats, CurrentUser currentUser) {
        this.checkIn = checkIn;
        this.stats = stats;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/checkin/scan")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return checkIn.scan(currentUser.id(), request);
    }

    @GetMapping("/api/v1/checkin/events/{eventId}/stats")
    public CheckInStatsResponse stats(@PathVariable UUID eventId) {
        return stats.stats(currentUser.id(), eventId);
    }
}
