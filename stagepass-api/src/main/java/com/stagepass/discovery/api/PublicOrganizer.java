package com.stagepass.discovery.api;

import com.stagepass.identity.domain.Organizer;

public record PublicOrganizer(String name, String slug, String logoUrl, String bio, String website) {

    public static PublicOrganizer from(Organizer o) {
        return new PublicOrganizer(o.getOrgName(), o.getSlug(), o.getLogoUrl(), o.getBio(), o.getWebsite());
    }
}
