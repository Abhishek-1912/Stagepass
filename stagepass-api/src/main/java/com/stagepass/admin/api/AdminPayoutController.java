package com.stagepass.admin.api;

import com.stagepass.admin.service.AdminPayoutService;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPayoutController {

    private final AdminPayoutService service;
    private final CurrentUser currentUser;

    public AdminPayoutController(AdminPayoutService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/payouts")
    public PageResponse<OrganizerPayoutSummary> summaries(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean onlyPayable, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.summaries(q, onlyPayable, page, size);
    }

    @PostMapping("/organizers/{id}/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutRow record(@PathVariable UUID id, @Valid @RequestBody RecordPayoutRequest request) {
        return service.record(currentUser.id(), id, request);
    }
}
