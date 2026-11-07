package com.stagepass.checkin.api;

import com.stagepass.checkin.service.CheckInService;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The door scanner: open to any signed-in user, access decided per event (organizer or door staff). */
@RestController
public class CheckInController {

    private final CheckInService checkIn;
    private final CurrentUser currentUser;

    public CheckInController(CheckInService checkIn, CurrentUser currentUser) {
        this.checkIn = checkIn;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/checkin/scan")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return checkIn.scan(currentUser.id(), request);
    }
}
