package com.stagepass.discovery.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** Date filters for discovery, always computed in India Standard Time regardless of the server clock. */
public final class DateRanges {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final java.util.Set<String> PRESETS = java.util.Set.of("today", "tomorrow", "weekend", "week", "month");

    /** Half-open range [start, end). */
    public record Range(OffsetDateTime start, OffsetDateTime end) {
    }

    private DateRanges() {
    }

    public static Optional<Range> resolve(String preset, LocalDate from, LocalDate to, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(IST).withNano(0);
        ZonedDateTime startOfToday = now.toLocalDate().atStartOfDay(IST);
        if (preset != null) {
            return Optional.of(switch (preset) {
                case "today" -> range(now, startOfToday.plusDays(1));
                case "tomorrow" -> range(startOfToday.plusDays(1), startOfToday.plusDays(2));
                case "weekend" -> weekend(now, startOfToday);
                case "week" -> range(now, now.plusDays(7));
                case "month" -> range(now, now.plusDays(30));
                default -> throw new IllegalArgumentException("Unknown date preset: " + preset);
            });
        }
        if (from == null && to == null) {
            return Optional.empty();
        }
        ZonedDateTime start = from == null ? now : from.atStartOfDay(IST);
        ZonedDateTime end = to == null ? start.plusYears(5) : to.plusDays(1).atStartOfDay(IST);
        return Optional.of(range(start, end));
    }

    private static Range weekend(ZonedDateTime now, ZonedDateTime startOfToday) {
        DayOfWeek day = now.getDayOfWeek();
        ZonedDateTime nextMonday = startOfToday.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return range(now, nextMonday);
        }
        ZonedDateTime saturday = startOfToday.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        return range(saturday, saturday.plusDays(2));
    }

    private static Range range(ZonedDateTime start, ZonedDateTime end) {
        return new Range(start.toOffsetDateTime(), end.toOffsetDateTime());
    }
}
