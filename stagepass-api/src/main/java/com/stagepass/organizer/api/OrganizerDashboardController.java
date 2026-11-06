package com.stagepass.organizer.api;

import com.stagepass.common.security.OrganizerGuard;
import com.stagepass.organizer.service.OrganizerAnalytics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer")
public class OrganizerDashboardController {

    private final OrganizerAnalytics analytics;
    private final OrganizerGuard guard;

    public OrganizerDashboardController(OrganizerAnalytics analytics, OrganizerGuard guard) {
        this.analytics = analytics;
        this.guard = guard;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@RequestParam(required = false) Integer days) {
        return analytics.overview(guard.requireApprovedOrganizerId(), days);
    }

    @GetMapping("/events/{eventId}/stats")
    public EventStatsResponse eventStats(@PathVariable UUID eventId, @RequestParam(required = false) Integer days) {
        return analytics.eventStats(guard.requireApprovedOrganizerId(), eventId, days);
    }
}
