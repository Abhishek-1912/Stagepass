package com.stagepass.organizer.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of the organizer profile: a missing (null) field is left unchanged, an empty string clears an optional
 * field. Name, slug, PAN and GSTIN are not here.
 */
public record OrganizationSettingsRequest(
        @Size(max = 1000) String bio,
        @Size(max = 500) String logoUrl,
        @Size(max = 200) String website,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = "^$|" + Formats.MOBILE, message = "must be a 10-digit Indian mobile number") String contactPhone,
        @Size(max = 120) String payoutAccountName,
        @Pattern(regexp = Formats.BANK_ACCOUNT, message = "must be 9–18 digits") String payoutAccountNumber,
        @Pattern(regexp = Formats.IFSC, message = "must be a valid IFSC, e.g. HDFC0001234") String payoutIfsc) {

    public OrganizationSettingsRequest {
        bio = strip(bio);
        website = strip(website);
        contactEmail = strip(contactEmail);
        contactPhone = strip(contactPhone);
        payoutAccountName = strip(payoutAccountName);
        payoutAccountNumber = payoutAccountNumber == null ? null : payoutAccountNumber.replaceAll("\\s+", "");
        payoutIfsc = Formats.upper(payoutIfsc);
    }

    /** Trims but keeps "" (meaning: clear the field). */
    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
