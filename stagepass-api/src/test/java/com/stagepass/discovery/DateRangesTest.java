package com.stagepass.discovery;

import com.stagepass.discovery.service.DateRanges;
import com.stagepass.discovery.service.DateRanges.Range;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DateRangesTest {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private static Clock at(String istLocal) {
        return Clock.fixed(OffsetDateTime.parse(istLocal + "+05:30").toInstant(), ZoneOffset.UTC);
    }

    private static OffsetDateTime ist(String local) {
        return OffsetDateTime.parse(local + "+05:30");
    }

    @Test
    void weekendFromWednesdayIsComingSatToMon() {
        Range r = DateRanges.resolve("weekend", null, null, at("2026-11-11T10:00:00")).orElseThrow();
        assertThat(r.start()).isEqualTo(ist("2026-11-14T00:00:00"));
        assertThat(r.end()).isEqualTo(ist("2026-11-16T00:00:00"));
    }

    @Test
    void weekendOnSundayStartsNow() {
        Range r = DateRanges.resolve("weekend", null, null, at("2026-11-15T09:00:00")).orElseThrow();
        assertThat(r.start()).isEqualTo(ist("2026-11-15T09:00:00"));
        assertThat(r.end()).isEqualTo(ist("2026-11-16T00:00:00"));
    }

    @Test
    void todayUsesIstNotUtc() {
        Clock utcEvening = Clock.fixed(Instant.parse("2026-11-11T20:00:00Z"), ZoneOffset.UTC); // 12 Nov 01:30 IST
        Range r = DateRanges.resolve("today", null, null, utcEvening).orElseThrow();
        assertThat(r.start()).isEqualTo(ist("2026-11-12T01:30:00"));
        assertThat(r.end()).isEqualTo(ist("2026-11-13T00:00:00"));
    }

    @Test
    void tomorrowIsTheNextIstDay() {
        Range r = DateRanges.resolve("tomorrow", null, null, at("2026-11-11T23:50:00")).orElseThrow();
        assertThat(r.start()).isEqualTo(ist("2026-11-12T00:00:00"));
        assertThat(r.end()).isEqualTo(ist("2026-11-13T00:00:00"));
    }

    @Test
    void customRangeIsInclusiveOfEndDate() {
        Range r = DateRanges.resolve(null, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-01"),
                at("2026-11-11T10:00:00")).orElseThrow();
        assertThat(r.start()).isEqualTo(ist("2026-12-01T00:00:00"));
        assertThat(r.end()).isEqualTo(ist("2026-12-02T00:00:00"));
    }

    @Test
    void noFilterMeansNoRange() {
        assertThat(DateRanges.resolve(null, null, null, at("2026-11-11T10:00:00"))).isEmpty();
    }

    @Test
    void rangeTimesAreInIst() {
        assertThat(DateRanges.resolve("week", null, null, at("2026-11-11T10:00:00")).orElseThrow().start().getOffset())
                .isEqualTo(IST);
    }
}
