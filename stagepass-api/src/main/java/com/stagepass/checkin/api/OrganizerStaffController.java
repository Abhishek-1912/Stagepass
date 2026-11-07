package com.stagepass.checkin.api;

import com.stagepass.checkin.service.StaffService;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The organizer's door team. */
@RestController
@RequestMapping("/api/v1/organizer/staff")
public class OrganizerStaffController {

    private final StaffService staff;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerStaffController(StaffService staff, OrganizerGuard guard, CurrentUser currentUser) {
        this.staff = staff;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return staff.list(guard.requireApprovedOrganizerId());
    }

    @PostMapping
    public ResponseEntity<StaffResponse> invite(@Valid @RequestBody StaffRequest request) {
        StaffService.Invited invited = staff.invite(guard.requireApprovedOrganizerId(), currentUser.id(), request.email(),
                request.eventId());
        return ResponseEntity.status(invited.created() ? HttpStatus.CREATED : HttpStatus.OK).body(invited.staff());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        staff.remove(guard.requireApprovedOrganizerId(), currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
