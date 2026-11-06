package com.stagepass.organizer.api;

/** The organizer's profile as they can see and edit it; the bank account number is only ever shown masked. */
public record OrganizationSettingsResponse(String orgName, String slug, String bio, String logoUrl, String website,
        String contactEmail, String contactPhone, String pan, String gstin, String payoutAccountName,
        String payoutAccountMasked, String payoutIfsc) {
}
