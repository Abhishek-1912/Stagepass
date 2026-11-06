package com.stagepass.identity.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** Organizer application form. Identifiers are trimmed and upper-cased before validation. */
public record OrganizerApplicationRequest(
        @NotBlank @Size(max = 120) String orgName,
        @Size(max = 2000) String bio,
        @URL @Size(max = 500) String logoUrl,
        @NotBlank @Email @Size(max = 200) String contactEmail,
        @Pattern(regexp = Formats.MOBILE, message = "must be a 10-digit Indian mobile number") String contactPhone,
        @URL @Size(max = 300) String website,
        @NotBlank @Pattern(regexp = Formats.PAN, message = "must be a valid PAN, e.g. ABCDE1234F") String pan,
        @Pattern(regexp = Formats.GSTIN, message = "must be a valid 15-character GSTIN") String gstin,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Size(max = 120) String payoutAccountName,
        @NotBlank @Pattern(regexp = Formats.BANK_ACCOUNT, message = "must be 9–18 digits") String payoutAccountNumber,
        @NotBlank @Pattern(regexp = Formats.IFSC, message = "must be a valid IFSC, e.g. HDFC0001234") String payoutIfsc) {

    public OrganizerApplicationRequest {
        orgName = Formats.trim(orgName);
        bio = Formats.trim(bio);
        logoUrl = Formats.trim(logoUrl);
        contactEmail = Formats.trim(contactEmail);
        contactPhone = Formats.trim(contactPhone);
        website = Formats.trim(website);
        pan = Formats.upper(pan);
        gstin = Formats.upper(gstin);
        state = Formats.trim(state);
        payoutAccountName = Formats.trim(payoutAccountName);
        payoutAccountNumber = payoutAccountNumber == null ? null : payoutAccountNumber.replaceAll("\\s+", "");
        payoutIfsc = Formats.upper(payoutIfsc);
    }
}
