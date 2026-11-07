package com.stagepass.admin.api;

import com.stagepass.organizer.service.DailySeries.DayPoint;

import java.util.List;

/** Platform numbers for the admin home. Money and counts cover the range; the queue counts are current. */
public record AdminOverview(int days, long gmvPaise, long platformRevenuePaise, long refundsPaise, long bookings,
        long ticketsSold, long newUsers, long totalUsers, long liveEvents, long pendingApplications, long pendingEvents,
        long openReports, long failedRefunds, long openDisputes, List<DayPoint> daily) {
}
