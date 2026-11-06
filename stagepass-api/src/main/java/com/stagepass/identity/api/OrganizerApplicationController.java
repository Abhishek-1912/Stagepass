package com.stagepass.identity.api;

import com.stagepass.common.security.CurrentUser;
import com.stagepass.identity.service.OrganizerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer-applications")
public class OrganizerApplicationController {

    private final OrganizerApplicationService applications;
    private final CurrentUser currentUser;

    public OrganizerApplicationController(OrganizerApplicationService applications, CurrentUser currentUser) {
        this.applications = applications;
        this.currentUser = currentUser;
    }

    /** 201 for a first application, 200 when a rejected applicant reapplies. */
    @PostMapping
    public ResponseEntity<OrganizerApplicationResponse> apply(@Valid @RequestBody OrganizerApplicationRequest request) {
        var result = applications.apply(currentUser.id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.application());
    }

    @GetMapping("/me")
    public OrganizerApplicationResponse mine() {
        return applications.mine(currentUser.id());
    }
}
