package com.stagepass.organizer.api;

import com.stagepass.organizer.service.DailySeries.DayPoint;

import java.util.List;
import java.util.UUID;

/** Sales analytics for one of the organizer's events. */
public record EventStatsResponse(int capacity, int ticketsSold, int held, int checkedIn, long grossSalesPaise,
        long refundedPaise, long netSalesPaise, long commissionPaise, List<TypeStats> byType, List<DayPoint> dailySales) {

    public record TypeStats(UUID ticketTypeId, String name, long pricePaise, int total, int sold, int held,
            long netSalesPaise) {
    }
}
