package com.stagepass.discovery.service;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.discovery.api.EventSearchParams;
import com.stagepass.discovery.api.PublicEventCard;
import com.stagepass.discovery.repository.PublicEventQueries;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Validates raw query parameters and runs the public search. */
@Service
public class EventSearchService {

    /** Raw query parameters exactly as received. */
    public record RawQuery(String q, String city, String category, String date, String from, String to,
            String minPrice, String maxPrice, String free, String lat, String lng, String radiusKm, String sort,
            Integer page, Integer size) {
    }

    private final PublicEventQueries queries;
    private final Clock clock;

    public EventSearchService(PublicEventQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public PageResponse<PublicEventCard> search(RawQuery raw) {
        FieldErrors errors = FieldErrors.start();
        String q = blankToNull(raw.q());
        errors.addIf(q != null && q.length() > 100, "q", "Search text can be at most 100 characters");
        String date = blankToNull(raw.date());
        errors.addIf(date != null && !DateRanges.PRESETS.contains(date), "date",
                "must be one of today, tomorrow, weekend, week, month");
        LocalDate from = parseDate(raw.from(), "from", errors);
        LocalDate to = parseDate(raw.to(), "to", errors);
        errors.addIf(from != null && to != null && to.isBefore(from), "to", "must be on or after 'from'");
        Long min = parseRupees(raw.minPrice(), "minPrice", errors);
        Long max = parseRupees(raw.maxPrice(), "maxPrice", errors);
        Double lat = parseDouble(raw.lat(), "lat", -90, 90, errors);
        Double lng = parseDouble(raw.lng(), "lng", -180, 180, errors);
        Double radius = parseDouble(raw.radiusKm(), "radiusKm", 1, 200, errors);
        EventSearchParams.Sort sort = EventSearchParams.Sort.POPULAR;
        if (blankToNull(raw.sort()) != null) {
            try {
                sort = EventSearchParams.Sort.valueOf(raw.sort().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add("sort", "must be popular, soonest or nearby");
            }
        }
        errors.addIf(sort == EventSearchParams.Sort.NEARBY && (lat == null || lng == null), "lat",
                "Sorting by distance needs lat and lng");
        errors.throwIfAny();

        var params = new EventSearchParams(q, blankToNull(raw.city()), blankToNull(raw.category()), min, max,
                "true".equalsIgnoreCase(raw.free()), lat, lng, radius == null ? 50 : radius, sort, null, null, false, false, false);
        var range = DateRanges.resolve(date, from, to, clock).orElse(null);
        return PageResponse.of(queries.search(params, range, PageRequests.of(raw.page(), raw.size(), Sort.unsorted())),
                c -> c);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate parseDate(String raw, String field, FieldErrors errors) {
        if (blankToNull(raw) == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            errors.add(field, "must be a date like 2026-12-31");
            return null;
        }
    }

    private static Long parseRupees(String raw, String field, FieldErrors errors) {
        if (blankToNull(raw) == null) {
            return null;
        }
        try {
            long rupees = Long.parseLong(raw.trim());
            if (rupees < 0 || rupees > 10_000_000) {
                errors.add(field, "must be between 0 and 1,00,00,000");
                return null;
            }
            return rupees * 100;
        } catch (NumberFormatException e) {
            errors.add(field, "must be a whole number of rupees");
            return null;
        }
    }

    private static Double parseDouble(String raw, String field, double min, double max, FieldErrors errors) {
        if (blankToNull(raw) == null) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            if (Double.isNaN(v) || v < min || v > max) {
                errors.add(field, "must be between " + (long) min + " and " + (long) max);
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            errors.add(field, "must be a number");
            return null;
        }
    }
}
