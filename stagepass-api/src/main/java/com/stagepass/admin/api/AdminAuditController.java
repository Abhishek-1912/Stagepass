package com.stagepass.admin.api;

import com.stagepass.admin.service.AuditLogQueries;
import com.stagepass.common.api.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AdminAuditController {

    private final AuditLogQueries queries;

    public AdminAuditController(AuditLogQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v1/admin/audit")
    public PageResponse<AuditRow> search(@RequestParam(required = false) String actor,
            @RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.search(actor, action, entityType, entityId, from, to, page, size);
    }
}
