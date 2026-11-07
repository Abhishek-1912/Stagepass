package com.stagepass.admin.api;

import com.stagepass.admin.service.PlatformAnalytics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOverviewController {

    private final PlatformAnalytics analytics;

    public AdminOverviewController(PlatformAnalytics analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/v1/admin/overview")
    public AdminOverview overview(@RequestParam(required = false) Integer days) {
        return analytics.overview(days);
    }
}
