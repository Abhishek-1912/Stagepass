package com.stagepass.admin.api;

import com.stagepass.admin.service.EventReportService;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.discovery.api.EventReportView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final EventReportService reports;
    private final CurrentUser currentUser;

    public AdminReportController(EventReportService reports, CurrentUser currentUser) {
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<AdminReportRow> list(@RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return reports.list(status, page, size);
    }

    @PostMapping("/{id}/resolve")
    public EventReportView resolve(@PathVariable UUID id, @Valid @RequestBody ReportDecisionRequest request) {
        return reports.resolve(currentUser.id(), id, request.note());
    }

    @PostMapping("/{id}/dismiss")
    public EventReportView dismiss(@PathVariable UUID id, @Valid @RequestBody ReportDecisionRequest request) {
        return reports.dismiss(currentUser.id(), id, request.note());
    }
}
