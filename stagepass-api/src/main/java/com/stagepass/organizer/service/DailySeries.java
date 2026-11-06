package com.stagepass.organizer.service;

import com.stagepass.common.error.FieldErrors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Day-by-day sales points for charts: zero-filled, oldest first, ending today (IST). */
public final class DailySeries {

    public record DayPoint(LocalDate date, int tickets, long amountPaise) {
    }

    private static final Set<Integer> RANGES = Set.of(7, 30, 90);

    private DailySeries() {
    }

    /** {@code null} → 30; anything but 7, 30 or 90 → 400 on field {@code days}. */
    public static int range(Integer days) {
        int d = days == null ? 30 : days;
        if (!RANGES.contains(d)) {
            throw FieldErrors.single("days", "must be 7, 30 or 90");
        }
        return d;
    }

    public static List<DayPoint> fill(Map<LocalDate, DayPoint> found, LocalDate today, int days) {
        List<DayPoint> out = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            out.add(found.getOrDefault(d, new DayPoint(d, 0, 0)));
        }
        return out;
    }
}
