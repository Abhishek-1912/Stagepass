package com.stagepass.checkin.service;

import com.stagepass.common.error.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Who may scan an event: its (approved) organizer, or active door staff of that organizer assigned to the event or
 * to all of the organizer's events. Decided from the database on every call, so removal takes effect at once.
 * Invites made before the person had an account are matched by their (verified) email and linked on first use.
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
    @Transactional
    public ScannableEvent requireScannable(UUID userId, UUID eventId) {
        linkPendingInvites(userId);
        List<ScannableEvent> found = sql.query("""
                select e.id, e.organizer_id, e.title, e.status, e.ends_at,
                       (o.user_id = :user and o.status in ('APPROVED', 'SUSPENDED')) as owner,
                       exists (select 1 from event_staff s
                               where s.organizer_id = e.organizer_id and s.status = 'ACTIVE' and s.user_id = :user
                                 and (s.event_id = e.id or s.event_id is null)) as staff
                from events e join organizers o on o.id = e.organizer_id
                where e.id = :event
                """, new MapSqlParameterSource("event", eventId).addValue("user", userId), (rs, n) -> {
            boolean owner = rs.getBoolean("owner");
            return owner || rs.getBoolean("staff")
                    ? new ScannableEvent(rs.getObject("id", UUID.class), rs.getObject("organizer_id", UUID.class),
                            rs.getString("title"), rs.getString("status"), rs.getObject("ends_at", OffsetDateTime.class), owner)
                    : null;
        });
        return found.stream().filter(e -> e != null).findFirst().orElseThrow(() -> ApiException.notFound("Event"));
    }

    /** Attaches active invites addressed to this user's email that have no account linked yet. */
    @Transactional
    public void linkPendingInvites(UUID userId) {
        sql.update("""
                update event_staff s set user_id = :user, accepted_at = now()
                from users u
                where u.id = :user and s.user_id is null and s.status = 'ACTIVE' and lower(s.email) = lower(u.email)
                """, new MapSqlParameterSource("user", userId));
    }
}
