package com.stagepass.notification.service;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.error.ApiException;
import com.stagepass.notification.api.NotificationView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * In-app notifications behind the bell (spec 8.9). {@link #notify} joins the caller's transaction, so a notification
 * commits with the change it describes; the dedupe key makes repeated triggers write one row.
 */
@Service
public class NotificationService {

    static final int TITLE_MAX = 120;
    static final int BODY_MAX = 300;

    private final NamedParameterJdbcTemplate sql;

    public NotificationService(NamedParameterJdbcTemplate sql) {
        this.sql = sql;
    }

    @Transactional
    public void notify(UUID userId, String type, String title, String body, String link, String dedupeKey) {
        if (userId == null) {
            return;
        }
        sql.update("""
                insert into notifications (user_id, type, title, body, link, dedupe_key)
                values (:user, :type, :title, :body, :link, :key)
                on conflict (dedupe_key) do nothing
                """, new MapSqlParameterSource("user", userId).addValue("type", type)
                .addValue("title", cut(title, TITLE_MAX)).addValue("body", cut(body, BODY_MAX)).addValue("link", link)
                .addValue("key", dedupeKey));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(UUID userId, Integer page, Integer size) {
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        MapSqlParameterSource p = new MapSqlParameterSource("user", userId);
        Long total = sql.queryForObject("select count(*) from notifications where user_id = :user", p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<NotificationView> rows = sql.query("""
                select id, type, title, body, link, created_at, read_at is not null as read from notifications
                where user_id = :user order by created_at desc, id limit :limit offset :offset
                """, p, (rs, n) -> new NotificationView(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("title"), rs.getString("body"), rs.getString("link"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getBoolean("read")));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        Long n = sql.queryForObject("select count(*) from notifications where user_id = :user and read_at is null",
                new MapSqlParameterSource("user", userId), Long.class);
        return n == null ? 0 : n;
    }

    /** 404 when the notification doesn't exist or belongs to someone else. */
    @Transactional
    public void markRead(UUID userId, UUID id) {
        int n = sql.update("""
                update notifications set read_at = coalesce(read_at, now()) where id = :id and user_id = :user
                """, new MapSqlParameterSource("id", id).addValue("user", userId));
        if (n == 0) {
            throw ApiException.notFound("Notification");
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        sql.update("update notifications set read_at = now() where user_id = :user and read_at is null",
                new MapSqlParameterSource("user", userId));
    }

    static String cut(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }
}
