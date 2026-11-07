package com.stagepass.checkin.service;

import com.stagepass.common.error.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Who may scan an event: its (approved) organizer, or active door staff of that organizer assigned to the event or
 * to all of the organizer's events. Decided from the database on every call, so removal takes effect at once.
 */
@Component
public class ScanAccess {

    /** An event the caller may scan. */
    public record ScannableEvent(UUID eventId, UUID organizerId, String title, String status, OffsetDateTime endsAt,
            boolean owner) {
    }

    private final NamedParameterJdbcTemplate sql;

    public ScanAccess(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    /** The event if the user may scan it; 404 otherwise (whether or not it exists). */
    public ScannableEvent requireScannable(UUID userId, UUID eventId) {
        List<ScannableEvent> found = sql.query("""
                select e.id, e.organizer_id, e.title, e.status, e.ends_at,
                       (o.user_id = :user and o.status = 'APPROVED') as owner
                from events e join organizers o on o.id = e.organizer_id
                where e.id = :event
                """, new MapSqlParameterSource("event", eventId).addValue("user", userId),
                (rs, n) -> new ScannableEvent(rs.getObject("id", UUID.class), rs.getObject("organizer_id", UUID.class),
                        rs.getString("title"), rs.getString("status"), rs.getObject("ends_at", OffsetDateTime.class),
                        rs.getBoolean("owner")));
        ScannableEvent e = found.stream().findFirst().orElseThrow(() -> ApiException.notFound("Event"));
        if (!e.owner()) {
            throw ApiException.notFound("Event");
        }
        return e;
    }
}
