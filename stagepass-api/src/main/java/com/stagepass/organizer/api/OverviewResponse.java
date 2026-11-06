package com.stagepass.organizer.api;

import com.stagepass.organizer.service.DailySeries.DayPoint;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Organizer overview: KPIs across all their events, recent bookings and daily sales. */
public record OverviewResponse(int liveEvents, int upcomingEvents, int ticketsSold, long grossSalesPaise,
        long netSalesPaise, long earningsPaise, List<RecentBooking> recentBookings, List<DayPoint> dailySales) {

    public record RecentBooking(UUID id, UUID eventId, String code, String eventTitle, String attendeeName,
            int ticketCount, long totalPaise, OffsetDateTime confirmedAt) {
    }
}
