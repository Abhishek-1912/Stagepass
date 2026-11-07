package com.stagepass.admin.service;

import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.api.OrganizerApplicationResponse;
import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.repository.OrganizerRepository;
import com.stagepass.identity.repository.UserRepository;
import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import com.stagepass.notification.service.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Admin decisions on organizer applications. */
@Service
public class AdminOrganizerService {

    private final OrganizerRepository organizers;
    private final UserRepository users;
    private final RoleLookupService roleLookup;
    private final AuditLogService audit;
    private final EmailOutbox outbox;
    private final PayoutCrypto crypto;
    private final NotificationService notifications;

    public AdminOrganizerService(OrganizerRepository organizers, UserRepository users, RoleLookupService roleLookup,
            AuditLogService audit, EmailOutbox outbox, PayoutCrypto crypto, NotificationService notifications) {
        this.notifications = notifications;
        this.organizers = organizers;
        this.users = users;
        this.roleLookup = roleLookup;
        this.audit = audit;
        this.outbox = outbox;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizerApplicationResponse> list(OrganizerStatus status, Pageable pageable) {
        return PageResponse.of(organizers.findByStatus(status, pageable),
                o -> OrganizerApplicationResponse.from(o, crypto));
    }

    @Transactional
    public OrganizerApplicationResponse approve(UUID adminId, UUID organizerId) {
        Organizer o = pending(organizerId);
        o.setStatus(OrganizerStatus.APPROVED);
        o.setReviewedBy(adminId);
        o.setReviewedAt(OffsetDateTime.now());
        users.grantRole(o.getUserId(), RoleName.ORGANIZER.name());
        audit.record(adminId, "ORGANIZER_APPROVED", "organizer", o.getId().toString(), Map.of("status", "PENDING"),
                Map.of("status", "APPROVED"));
        audit.record(adminId, "ROLE_GRANTED", "user", o.getUserId().toString(), null,
                Map.of("role", RoleName.ORGANIZER.name()));
        outbox.enqueue(o.getContactEmail(), EmailTemplate.ORGANIZER_APPROVED, Map.of("orgName", o.getOrgName()));
        notifications.notify(o.getUserId(), "ORGANIZER_APPROVED", "You're approved to sell on Stagepass",
                o.getOrgName() + " can now create events and sell tickets.", "/organizer",
                "organizer-decision:" + o.getId() + ":APPROVED");
        roleLookup.evictAfterCommit(o.getUserId());
        return OrganizerApplicationResponse.from(o, crypto);
    }

    @Transactional
    public OrganizerApplicationResponse reject(UUID adminId, UUID organizerId, String reason) {
        Organizer o = pending(organizerId);
        o.setStatus(OrganizerStatus.REJECTED);
        o.setRejectionReason(reason);
        o.setReviewedBy(adminId);
        o.setReviewedAt(OffsetDateTime.now());
        audit.record(adminId, "ORGANIZER_REJECTED", "organizer", o.getId().toString(), Map.of("status", "PENDING"),
                Map.of("status", "REJECTED", "reason", reason));
        outbox.enqueue(o.getContactEmail(), EmailTemplate.ORGANIZER_REJECTED,
                Map.of("orgName", o.getOrgName(), "reason", reason));
        notifications.notify(o.getUserId(), "ORGANIZER_REJECTED", "Update on your organizer application",
                "Reason: " + reason, "/become-organizer", "organizer-decision:" + o.getId() + ":REJECTED");
        return OrganizerApplicationResponse.from(o, crypto);
    }

    private Organizer pending(UUID organizerId) {
        Organizer o = organizers.findById(organizerId).orElseThrow(() -> ApiException.notFound("Application"));
        if (o.getStatus() != OrganizerStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE,
                    "This application was already " + o.getStatus().name().toLowerCase() + ".");
        }
        return o;
    }
}
