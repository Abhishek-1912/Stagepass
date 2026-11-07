package com.stagepass.engagement.service;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.error.ApiException;
import com.stagepass.discovery.repository.PublicEventQueries;
import com.stagepass.engagement.api.SavedEventView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Saved events (spec 9): a heart on public events; only events visible in discovery can be saved. */
@Service
public class SavedEventService {

    private final NamedParameterJdbcTemplate sql;

    public SavedEventService(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    @Transactional
    public void save(UUID userId, UUID eventId) {
        int n = sql.update("""
                insert into saved_events (user_id, event_id)
                select :user, e.id from events e join organizers o on o.id = e.organizer_id
                where e.id = :event and e.status = 'PUBLISHED' and e.ends_at > now() and o.status = 'APPROVED'
                on conflict do nothing
                """, params(userId, eventId));
        if (n == 0) {
            Boolean already = sql.queryForObject(
                    "select exists (select 1 from saved_events where user_id = :user and event_id = :event)",
                    params(userId, eventId), Boolean.class);
            if (!Boolean.TRUE.equals(already)) {
                throw ApiException.notFound("Event");
            }
        }
    }

    @Transactional
    public void unsave(UUID userId, UUID eventId) {
        sql.update("delete from saved_events where user_id = :user and event_id = :event", params(userId, eventId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SavedEventView> list(UUID userId, Integer page, Integer size) {
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        MapSqlParameterSource p = new MapSqlParameterSource("user", userId);
        Long total = sql.queryForObject("""
                select count(*) from saved_events s join events e on e.id = s.event_id
                join organizers o on o.id = e.organizer_id where s.user_id = :user and o.status <> 'SUSPENDED'
                """, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<SavedEventView> rows = sql.query("""
                select e.id, e.slug, e.title, e.summary, e.cover_image_url, e.starts_at, e.ends_at, e.is_online,
                       e.is_featured, v.name as venue_name, v.city, c.slug as category_slug, c.name as category_name,
                       t.min_price, t.available, cast(null as double precision) as distance_km,
                       e.status, s.created_at as saved_at
                from saved_events s
                join events e on e.id = s.event_id
                join organizers o on o.id = e.organizer_id and o.status <> 'SUSPENDED'
                join event_categories c on c.id = e.category_id
                left join venues v on v.id = e.venue_id
                left join lateral (
                    select min(tt.price_paise) filter (where tt.status = 'ACTIVE') as min_price,
                           coalesce(sum(greatest(ti.total - ti.sold - ti.held, 0))
                                    filter (where tt.status = 'ACTIVE'
                                            and (tt.sale_ends_at is null or tt.sale_ends_at > now())), 0) as available
                    from ticket_types tt join ticket_inventory ti on ti.ticket_type_id = tt.id where tt.event_id = e.id
                ) t on true
                where s.user_id = :user
                order by s.created_at desc, e.id limit :limit offset :offset
                """, p, (rs, n) -> new SavedEventView(PublicEventQueries.CARD.mapRow(rs, n), rs.getString("status"),
                rs.getObject("saved_at", OffsetDateTime.class)));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }

    /** Up to 500 most recently saved event ids, to fill hearts on cards. */
    @Transactional(readOnly = true)
    public List<UUID> ids(UUID userId) {
        return sql.queryForList("""
                select event_id from saved_events where user_id = :user order by created_at desc limit 500
                """, new MapSqlParameterSource("user", userId), UUID.class);
    }

    private static MapSqlParameterSource params(UUID userId, UUID eventId) {
        return new MapSqlParameterSource("user", userId).addValue("event", eventId);
    }
}
