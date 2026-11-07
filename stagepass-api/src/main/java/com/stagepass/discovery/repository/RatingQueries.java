package com.stagepass.discovery.repository;

import com.stagepass.discovery.api.RatingSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Star-rating summaries from the reviews table, for event pages and organizer profiles. */
@Repository
public class RatingQueries {

    private final JdbcTemplate jdbc;

    public RatingQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RatingSummary forEvent(UUID eventId) {
        return jdbc.queryForObject("select round(avg(rating)::numeric, 1), count(*) from reviews where event_id = ?",
                (rs, n) -> summary(rs.getBigDecimal(1), rs.getLong(2)), eventId);
    }

    public RatingSummary forOrganizer(UUID organizerId) {
        return jdbc.queryForObject("""
                select round(avg(r.rating)::numeric, 1), count(*) from reviews r join events e on e.id = r.event_id
                where e.organizer_id = ?
                """, (rs, n) -> summary(rs.getBigDecimal(1), rs.getLong(2)), organizerId);
    }

    private static RatingSummary summary(BigDecimal avg, long count) {
        return new RatingSummary(count == 0 || avg == null ? null : avg.doubleValue(), count);
    }
}
