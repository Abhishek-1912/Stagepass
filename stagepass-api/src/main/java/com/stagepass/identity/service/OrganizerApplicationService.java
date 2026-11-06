package com.stagepass.identity.service;

import com.stagepass.catalog.service.ImageUrlPolicy;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.text.Slugs;
import com.stagepass.identity.api.OrganizerApplicationRequest;
import com.stagepass.identity.api.OrganizerApplicationResponse;
import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.repository.OrganizerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Attendee side of organizer onboarding: apply, reapply after rejection, view own application. */
@Service
public class OrganizerApplicationService {

    public record ApplyResult(OrganizerApplicationResponse application, boolean created) {
    }

    private final OrganizerRepository organizers;
    private final PayoutCrypto crypto;
    private final AuditLogService audit;
    private final ImageUrlPolicy imageUrls;

    public OrganizerApplicationService(OrganizerRepository organizers, PayoutCrypto crypto, AuditLogService audit,
            ImageUrlPolicy imageUrls) {
        this.organizers = organizers;
        this.crypto = crypto;
        this.audit = audit;
        this.imageUrls = imageUrls;
    }

    @Transactional
    public ApplyResult apply(UUID userId, OrganizerApplicationRequest request) {
        if (request.logoUrl() != null
                && !imageUrls.isOwned(request.logoUrl(), ImageUrlPolicy.organizerFolder(userId))) {
            throw FieldErrors.single("logoUrl", "Upload the logo through Stagepass (jpg, png or webp)");
        }
        Optional<Organizer> existing = organizers.findByUserId(userId);
        Organizer organizer;
        boolean created;
        if (existing.isEmpty()) {
            organizer = new Organizer();
            organizer.setUserId(userId);
            organizer.setSlug(Slugs.unique(Slugs.slugify(request.orgName())));
            created = true;
        } else {
            organizer = existing.get();
            switch (organizer.getStatus()) {
                case PENDING -> throw ApiException.conflict("Your application is already under review.");
                case APPROVED -> throw ApiException.conflict("You are already an approved organizer.");
                case SUSPENDED -> throw ApiException.forbidden("Your organizer account is suspended. Contact support.");
                case REJECTED -> { /* reapply below */ }
            }
            created = false;
        }
        apply(organizer, request);
        organizer.setStatus(OrganizerStatus.PENDING);
        organizer.setRejectionReason(null);
        organizer.setReviewedAt(null);
        organizer.setReviewedBy(null);
        organizer.setAppliedAt(OffsetDateTime.now());
        organizers.saveAndFlush(organizer);
        audit.record(userId, "ORGANIZER_APPLIED", "organizer", organizer.getId().toString(), null,
                Map.of("orgName", organizer.getOrgName(), "reapplied", !created));
        return new ApplyResult(OrganizerApplicationResponse.from(organizer, crypto), created);
    }

    @Transactional(readOnly = true)
    public OrganizerApplicationResponse mine(UUID userId) {
        return organizers.findByUserId(userId)
                .map(o -> OrganizerApplicationResponse.from(o, crypto))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "You haven't applied to become an organizer"));
    }

    private void apply(Organizer o, OrganizerApplicationRequest r) {
        o.setOrgName(r.orgName());
        o.setBio(r.bio());
        o.setLogoUrl(r.logoUrl());
        o.setContactEmail(r.contactEmail());
        o.setContactPhone(r.contactPhone());
        o.setWebsite(r.website());
        o.setPan(r.pan());
        o.setGstin(r.gstin());
        o.setState(r.state());
        o.setPayoutAccountName(r.payoutAccountName());
        o.setPayoutAccountNumberEnc(crypto.encrypt(r.payoutAccountNumber()));
        o.setPayoutIfsc(r.payoutIfsc());
    }
}
