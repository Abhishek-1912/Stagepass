package com.stagepass.organizer.service;

import com.stagepass.catalog.service.ImageUrlPolicy;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.repository.OrganizerRepository;
import com.stagepass.organizer.api.OrganizationSettingsRequest;
import com.stagepass.organizer.api.OrganizationSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** The organizer's own profile and payout details (name, slug, PAN and GSTIN change only through admin). */
@Service
public class OrganizationSettingsService {

    private final OrganizerRepository organizers;
    private final PayoutCrypto crypto;
    private final ImageUrlPolicy images;
    private final AuditLogService audit;

    public OrganizationSettingsService(OrganizerRepository organizers, PayoutCrypto crypto, ImageUrlPolicy images,
            AuditLogService audit) {
        this.organizers = organizers;
        this.crypto = crypto;
        this.images = images;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse get(UUID organizerId) {
        return view(organizers.findById(organizerId).orElseThrow());
    }

    @Transactional
    public OrganizationSettingsResponse update(UUID organizerId, UUID actorId, OrganizationSettingsRequest r) {
        Organizer o = organizers.findById(organizerId).orElseThrow();
        FieldErrors errors = FieldErrors.start();
        if (r.logoUrl() != null && !r.logoUrl().isBlank()
                && !images.isOwned(r.logoUrl(), ImageUrlPolicy.organizerFolder(o.getUserId()))) {
            errors.add("logoUrl", "must be an image uploaded to your Stagepass organizer folder");
        }
        if (r.website() != null && !r.website().isBlank() && !isHttpsUrl(r.website())) {
            errors.add("website", "must be a full https:// address");
        }
        if (r.contactEmail() != null && r.contactEmail().isBlank()) {
            errors.add("contactEmail", "is required - attendees and Stagepass use it to reach you");
        }
        if (r.payoutAccountNumber() != null && (r.payoutIfsc() == null || r.payoutIfsc().isBlank())) {
            errors.add("payoutIfsc", "is required when changing the account number");
        }
        errors.throwIfAny();

        Map<String, Object> before = payoutView(o);
        if (r.bio() != null) {
            o.setBio(r.bio().isBlank() ? null : r.bio());
        }
        if (r.logoUrl() != null) {
            o.setLogoUrl(r.logoUrl().isBlank() ? null : r.logoUrl());
        }
        if (r.website() != null) {
            o.setWebsite(r.website().isBlank() ? null : r.website());
        }
        if (r.contactEmail() != null) {
            o.setContactEmail(r.contactEmail());
        }
        if (r.contactPhone() != null) {
            o.setContactPhone(r.contactPhone().isBlank() ? null : r.contactPhone());
        }
        if (r.payoutAccountName() != null && !r.payoutAccountName().isBlank()) {
            // The payout account always needs a holder name, so blank means "no change" here.
            o.setPayoutAccountName(r.payoutAccountName());
        }
        if (r.payoutAccountNumber() != null) {
            o.setPayoutAccountNumberEnc(crypto.encrypt(r.payoutAccountNumber()));
        }
        if (r.payoutIfsc() != null) {
            o.setPayoutIfsc(r.payoutIfsc());
        }
        organizers.saveAndFlush(o);
        Map<String, Object> after = payoutView(o);
        if (!Objects.equals(before, after) || r.payoutAccountNumber() != null) {
            audit.record(actorId, "PAYOUT_DETAILS_CHANGED", "organizer", o.getId().toString(), before, after);
        }
        return view(o);
    }

    private Map<String, Object> payoutView(Organizer o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountName", o.getPayoutAccountName());
        m.put("account", masked(o));
        m.put("ifsc", o.getPayoutIfsc());
        return m;
    }

    private String masked(Organizer o) {
        return o.getPayoutAccountNumberEnc() == null ? null : PayoutCrypto.mask(crypto.decrypt(o.getPayoutAccountNumberEnc()));
    }

    private OrganizationSettingsResponse view(Organizer o) {
        return new OrganizationSettingsResponse(o.getOrgName(), o.getSlug(), o.getBio(), o.getLogoUrl(), o.getWebsite(),
                o.getContactEmail(), o.getContactPhone(), o.getPan(), o.getGstin(), o.getPayoutAccountName(), masked(o),
                o.getPayoutIfsc());
    }

    private static boolean isHttpsUrl(String s) {
        try {
            URI uri = URI.create(s);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getHost().contains(".");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
