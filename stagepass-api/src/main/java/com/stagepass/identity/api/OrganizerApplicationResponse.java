package com.stagepass.identity.api;

import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.identity.domain.Organizer;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An organizer application as its owner or an admin sees it. The bank account is always masked. */
public record OrganizerApplicationResponse(UUID id, UUID userId, String orgName, String slug, String status,
        String rejectionReason, String contactEmail, String contactPhone, String website, String bio, String logoUrl,
        String pan, String gstin, String state, String payoutAccountName, String payoutAccountMasked,
        String payoutIfsc, OffsetDateTime appliedAt, OffsetDateTime reviewedAt) {

    public static OrganizerApplicationResponse from(Organizer o, PayoutCrypto crypto) {
        String masked = o.getPayoutAccountNumberEnc() == null ? null
                : PayoutCrypto.mask(crypto.decrypt(o.getPayoutAccountNumberEnc()));
        return new OrganizerApplicationResponse(o.getId(), o.getUserId(), o.getOrgName(), o.getSlug(),
                o.getStatus().name(), o.getRejectionReason(), o.getContactEmail(), o.getContactPhone(),
                o.getWebsite(), o.getBio(), o.getLogoUrl(), o.getPan(), o.getGstin(), o.getState(),
                o.getPayoutAccountName(), masked, o.getPayoutIfsc(), o.getAppliedAt(), o.getReviewedAt());
    }
}
