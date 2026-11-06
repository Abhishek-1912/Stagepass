package com.stagepass.discovery.api;

import java.util.List;

/** Everything the homepage needs in one call. Empty lists mean "hide the section". */
public record HomeFeedResponse(List<PublicEventCard> featured, List<PublicEventCard> trending,
        List<PublicEventCard> thisWeekend, List<PublicEventCard> upcoming, List<PublicEventCard> nearby,
        List<CategoryRail> categories) {

    public record CategoryRail(String slug, String name, String icon, List<PublicEventCard> events) {
    }
}
