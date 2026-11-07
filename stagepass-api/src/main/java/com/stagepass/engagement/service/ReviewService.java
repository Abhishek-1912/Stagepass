package com.stagepass.engagement.service;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.engagement.api.MyReview;
import com.stagepass.engagement.api.PublicReview;
import com.stagepass.engagement.api.ReviewRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event reviews (spec 7 {@code reviews}): written by people who held a live ticket, after the event ended; one per
 * person per event, editable. Shown publicly with the reviewer's first name only.
 */
@Service
public class ReviewService {

    public record Saved(PublicReview review, boolean created) {
    }

    static final String NOT_ELIGIBLE = "You can review events you attended, after they end";

    private static final String SELECT = """
            select r.id, r.rating, r.comment, u.full_name, r.created_at, r.updated_at
            from reviews r join users u on u.id = r.user_id
            """;

    private static final RowMapper<PublicReview> REVIEW = (rs, n) -> new PublicReview(rs.getObject("id", UUID.class),
            rs.getInt("rating"), rs.getString("comment"), firstName(rs.getString("full_name")),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;

    public ReviewService(NamedParameterJdbcTemplate sql, AuditLogService audit) {
        this.sql = sql;
        this.audit = audit;
    }

    @Transactional
    public Saved upsert(UUID userId, UUID eventId, ReviewRequest r) {
        if (!eligible(userId, eventId)) {
            throw ApiException.forbidden(NOT_ELIGIBLE);
        }
        String comment = r.comment() == null || r.comment().isBlank() ? null : r.comment().strip();
        Map<String, Object> row = sql.queryForMap("""
                insert into reviews (event_id, user_id, rating, comment) values (:event, :user, :rating, :comment)
                on conflict (event_id, user_id) do update set rating = excluded.rating, comment = excluded.comment
                returning id, (xmax = 0) as created
                """, new MapSqlParameterSource("event", eventId).addValue("user", userId).addValue("rating", r.rating())
                .addValue("comment", comment));
        UUID id = (UUID) row.get("id");
        return new Saved(byId(id), Boolean.TRUE.equals(row.get("created")));
    }

    @Transactional
    public void delete(UUID userId, UUID eventId) {
        sql.update("delete from reviews where event_id = :event and user_id = :user",
                new MapSqlParameterSource("event", eventId).addValue("user", userId));
    }

    @Transactional(readOnly = true)
    public MyReview mine(UUID userId, UUID eventId) {
        List<PublicReview> found = sql.query(SELECT + " where r.event_id = :event and r.user_id = :user",
                new MapSqlParameterSource("event", eventId).addValue("user", userId), REVIEW);
        return new MyReview(eligible(userId, eventId), found.isEmpty() ? null : found.get(0));
    }

    /** Reviews of a publicly visible event, newest first; 404 for events discovery would not show. */
    @Transactional(readOnly = true)
    public PageResponse<PublicReview> forEvent(String slug, Integer page, Integer size) {
        List<UUID> event = sql.queryForList("""
                select e.id from events e join organizers o on o.id = e.organizer_id
                where e.slug = :slug and e.status in ('PUBLISHED', 'CANCELLED', 'COMPLETED') and o.status <> 'SUSPENDED'
                """, new MapSqlParameterSource("slug", slug), UUID.class);
        if (event.isEmpty()) {
            throw ApiException.notFound("Event");
        }
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        MapSqlParameterSource p = new MapSqlParameterSource("event", event.get(0));
        Long total = sql.queryForObject("select count(*) from reviews where event_id = :event", p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<PublicReview> rows = sql.query(SELECT + " where r.event_id = :event order by r.created_at desc, r.id"
                + " limit :limit offset :offset", p, REVIEW);
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }

    @Transactional
    public void adminDelete(UUID adminId, UUID reviewId) {
        List<Map<String, Object>> found = sql.queryForList(
                "delete from reviews where id = :id returning event_id, user_id, rating, comment",
                new MapSqlParameterSource("id", reviewId));
        if (found.isEmpty()) {
            throw ApiException.notFound("Review");
        }
        Map<String, Object> r = found.get(0);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("eventId", r.get("event_id").toString());
        before.put("userId", r.get("user_id").toString());
        before.put("rating", r.get("rating"));
        before.put("comment", r.get("comment"));
        audit.record(adminId, "REVIEW_REMOVED", "review", reviewId.toString(), before, null);
    }

    /** Held a live ticket (valid or used, booking not fully refunded) for an event that has ended and wasn't cancelled. */
    private boolean eligible(UUID userId, UUID eventId) {
        Boolean ok = sql.queryForObject("""
                select exists (
                    select 1 from bookings b join events e on e.id = b.event_id
                    where b.user_id = :user and b.event_id = :event
                      and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED')
                      and e.status in ('PUBLISHED', 'COMPLETED') and e.ends_at < now()
                      and exists (select 1 from tickets t where t.booking_id = b.id and t.status in ('VALID', 'CHECKED_IN')))
                """, new MapSqlParameterSource("user", userId).addValue("event", eventId), Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    private PublicReview byId(UUID id) {
        return sql.queryForObject(SELECT + " where r.id = :id", new MapSqlParameterSource("id", id), REVIEW);
    }

    static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Stagepass guest";
        }
        return fullName.strip().split("\\s+")[0];
    }
}
