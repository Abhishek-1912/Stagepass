package com.stagepass.admin.service;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.error.FieldErrors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** Shared helpers for the admin list endpoints: paging, enum filters and IST date ranges. */
final class AdminQueries {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final String LIKE_ESCAPE = " escape '\\'";

    private AdminQueries() {
    }

    /** Runs {@code select count(*) from} + body and the page query; body holds the from/join/where part. */
    static <T> PageResponse<T> page(NamedParameterJdbcTemplate sql, String select, String body, String order,
            MapSqlParameterSource p, Integer page, Integer size, RowMapper<T> mapper) {
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        Long total = sql.queryForObject("select count(*) " + body, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<T> rows = sql.query(select + " " + body + " order by " + order + " limit :limit offset :offset", p, mapper);
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }

    /** The enum constant's name for a case-insensitive value, or 400 on {@code field}. */
    static <E extends Enum<E>> String enumName(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw FieldErrors.single(field, "is not a valid value");
        }
    }

    /** One of the allowed values (case-insensitive), or 400 on {@code field}. */
    static String oneOf(List<String> allowed, String value, String field) {
        String v = value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw FieldErrors.single(field, "is not a valid value");
        }
        return v;
    }

    static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    /** Adds {@code fromTs}/{@code toTs} (IST, inclusive dates) when given; to before from or a span over 366 days → 400. */
    static String dateRange(String column, LocalDate from, LocalDate to, MapSqlParameterSource p) {
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw FieldErrors.single("to", "must not be before from");
            }
            if (ChronoUnit.DAYS.between(from, to) > 366) {
                throw FieldErrors.single("to", "must be at most 366 days after from");
            }
        }
        StringBuilder where = new StringBuilder();
        if (from != null) {
            p.addValue("fromTs", startOf(from));
            where.append(" and ").append(column).append(" >= :fromTs");
        }
        if (to != null) {
            p.addValue("toTs", startOf(to.plusDays(1)));
            where.append(" and ").append(column).append(" < :toTs");
        }
        return where.toString();
    }

    static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(IST).toOffsetDateTime();
    }
}
