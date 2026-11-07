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
        List<ScannerEvent> events = sql.query("""
                select e.id, e.title, e.starts_at, e.ends_at, o.org_name,
                       case when e.is_online or v.id is null then 'Online' else v.name || ', ' || v.city end as venue,
                       (o.user_id = :user and o.status in ('APPROVED', 'SUSPENDED')) as owner
                from events e
                join organizers o on o.id = e.organizer_id
                left join venues v on v.id = e.venue_id
                where ((o.user_id = :user and o.status in ('APPROVED', 'SUSPENDED'))
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
        if (events.isEmpty()) {
            return events;
        }
        java.util.Map<UUID, List<GateOption>> gates = new java.util.HashMap<>();
        sql.query("""
                select g.event_id, g.id, g.name,
                       coalesce((select array_agg(tt.name order by tt.sort_order, tt.name)
                                 from gate_ticket_types x join ticket_types tt on tt.id = x.ticket_type_id
                                 where x.gate_id = g.id), '{}'::text[]) as types
                from gates g where g.event_id in (:events)
                order by g.sort_order, g.name
                """, new MapSqlParameterSource("events", events.stream().map(ScannerEvent::id).toList()), rs -> {
            gates.computeIfAbsent(rs.getObject("event_id", UUID.class), k -> new java.util.ArrayList<>())
                    .add(new GateOption(rs.getObject("id", UUID.class), rs.getString("name"),
                            java.util.Arrays.asList((String[]) rs.getArray("types").getArray())));
        });
        return events.stream().map(e -> new ScannerEvent(e.id(), e.title(), e.startsAt(), e.endsAt(), e.venue(),
                e.organizerName(), e.owner(), gates.getOrDefault(e.id(), List.of()))).toList();
    }
}
