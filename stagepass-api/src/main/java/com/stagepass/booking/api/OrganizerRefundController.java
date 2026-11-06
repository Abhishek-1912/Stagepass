package com.stagepass.booking.api;

import com.stagepass.booking.service.RefundService;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.common.security.OrganizerGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Refund requests on the organizer's own events. */
@RestController
@RequestMapping("/api/v1/organizer")
public class OrganizerRefundController {

    private final RefundService refunds;
    private final OrganizerGuard guard;
    private final CurrentUser currentUser;

    public OrganizerRefundController(RefundService refunds, OrganizerGuard guard, CurrentUser currentUser) {
        this.refunds = refunds;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @GetMapping("/events/{eventId}/refund-requests")
    public List<OrganizerRefundView> list(@PathVariable UUID eventId) {
        return refunds.listForEvent(guard.requireApprovedOrganizerId(), eventId);
    }

    @PostMapping("/refund-requests/{refundId}/approve")
    public OrganizerRefundView approve(@PathVariable UUID refundId,
            @Valid @RequestBody(required = false) RefundDecisionRequest request) {
        return refunds.approve(guard.requireApprovedOrganizerId(), currentUser.id(), refundId);
    }

    @PostMapping("/refund-requests/{refundId}/decline")
    public OrganizerRefundView decline(@PathVariable UUID refundId,
            @Valid @RequestBody(required = false) RefundDecisionRequest request) {
        return refunds.decline(guard.requireApprovedOrganizerId(), currentUser.id(), refundId,
                request == null ? null : request.note());
    }
}
