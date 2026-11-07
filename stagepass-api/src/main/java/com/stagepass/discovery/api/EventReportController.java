package com.stagepass.discovery.api;

import com.stagepass.admin.service.EventReportService;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A signed-in user flags an event for the Stagepass team. */
@RestController
public class EventReportController {

    private final EventReportService reports;
    private final CurrentUser currentUser;

    public EventReportController(EventReportService reports, CurrentUser currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/events/{eventId}/reports")
    public ResponseEntity<EventReportView> report(@PathVariable UUID eventId, @Valid @RequestBody EventReportRequest request) {
        EventReportService.Reported r = reports.report(currentUser.id(), eventId, request);
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r.report());
    }
}
