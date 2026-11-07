package com.stagepass.checkin.api;

import com.stagepass.checkin.service.ScanAccess;
import com.stagepass.common.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Events the signed-in user can open the scanner for: their own, or ones they are on the door team for. */
@RestController
public class ScannerEventsController {

    public record GateOption(UUID id, String name, List<String> ticketTypeNames) {
    }

    public record ScannerEvent(UUID id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt, String venue,
            String organizerName, boolean owner, List<GateOption> gates) {
    }

    private final NamedParameterJdbcTemplate sql;
    private final ScanAccess access;
    private final CurrentUser currentUser;

    public ScannerEventsController(NamedParameterJdbcTemplate sql, ScanAccess access, CurrentUser currentUser) {
        this.sql = sql;
        this.access = access;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/checkin/events")
    @Transactional
    public List<ScannerEvent> events() {
        UUID user = currentUser.id();
        access.linkPendingInvites(user);
        return sql.query("""
                select e.id, e.title, e.starts_at, e.ends_at, o.org_name,
                       case when e.is_online or v.id is null then 'Online' else v.name || ', ' || v.city end as venue,
                       (o.user_id = :user and o.status = 'APPROVED') as owner
                from events e
                join organizers o on o.id = e.organizer_id
                left join venues v on v.id = e.venue_id
                where ((o.user_id = :user and o.status = 'APPROVED')
                       or exists (select 1 from event_staff s
                                  where s.organizer_id = e.organizer_id and s.status = 'ACTIVE' and s.user_id = :user
                                    and (s.event_id = e.id or s.event_id is null)))
                  and ((e.status = 'PUBLISHED' and e.starts_at <= now() + interval '30 days')
                       or (e.status = 'COMPLETED' and e.ends_at > now() - interval '12 hours'))
                order by e.starts_at, e.title
                """, new MapSqlParameterSource("user", user), (rs, n) -> new ScannerEvent(rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class), rs.getString("venue"), rs.getString("org_name"),
                rs.getBoolean("owner"), List.of()));
    }
}
