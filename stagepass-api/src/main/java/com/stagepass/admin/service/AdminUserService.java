package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminUserRow;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.LikePatterns;
import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.domain.UserStatus;
import com.stagepass.identity.service.RoleLookupService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin view of accounts: search, suspension and the ADMIN role (spec 6, 9). */
@Service
public class AdminUserService {

    private static final String SELECT = """
            select u.id, u.email, u.full_name, u.status, u.created_at, u.suspended_at, u.suspension_reason,
                   array(select r.name from user_roles ur join roles r on r.id = ur.role_id
                         where ur.user_id = u.id order by r.name) as roles
            """;

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;
    private final RoleLookupService roleLookup;

    public AdminUserService(NamedParameterJdbcTemplate sql, AuditLogService audit, RoleLookupService roleLookup) {
        this.sql = sql;
        this.audit = audit;
        this.roleLookup = roleLookup;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserRow> list(String q, String role, String status, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder("from users u where true");
        if (AdminQueries.present(q)) {
            p.addValue("q", LikePatterns.contains(q));
            body.append(" and (lower(u.email) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(coalesce(u.full_name, '')) like :q").append(AdminQueries.LIKE_ESCAPE).append(")");
        }
        if (AdminQueries.present(role)) {
            p.addValue("role", AdminQueries.enumName(RoleName.class, role, "role"));
            body.append(" and exists (select 1 from user_roles ur join roles r on r.id = ur.role_id"
                    + " where ur.user_id = u.id and r.name = :role)");
        }
        if (AdminQueries.present(status)) {
            p.addValue("status", AdminQueries.enumName(UserStatus.class, status, "status"));
            body.append(" and u.status = :status");
        }
        return AdminQueries.page(sql, SELECT, body.toString(), "u.created_at desc, u.id", p, page, size,
                (rs, n) -> row(rs));
    }

    @Transactional
    public AdminUserRow suspend(UUID adminId, UUID userId, String reason) {
        AdminUserRow u = lock(userId);
        if (userId.equals(adminId)) {
            throw new ApiException(ErrorCode.INVALID_STATE, "You can't suspend your own account");
        }
        if (u.roles().contains(RoleName.ADMIN.name())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Remove admin from this account first");
        }
        if (UserStatus.SUSPENDED.name().equals(u.status())) {
            return u;
        }
        String r = reason.strip();
        sql.update("""
                update users set status = 'SUSPENDED', suspended_at = now(), suspension_reason = :reason,
                       version = version + 1 where id = :id
                """, new MapSqlParameterSource("id", userId).addValue("reason", r));
        audit.record(adminId, "USER_SUSPENDED", "user", userId.toString(), Map.of("status", u.status()),
                Map.of("status", "SUSPENDED", "reason", r));
        roleLookup.evictAfterCommit(userId);
        return get(userId);
    }

    @Transactional
    public AdminUserRow reactivate(UUID adminId, UUID userId) {
        AdminUserRow u = lock(userId);
        if (UserStatus.ACTIVE.name().equals(u.status())) {
            return u;
        }
        sql.update("""
                update users set status = 'ACTIVE', suspended_at = null, suspension_reason = null,
                       version = version + 1 where id = :id
                """, new MapSqlParameterSource("id", userId));
        audit.record(adminId, "USER_REACTIVATED", "user", userId.toString(), Map.of("status", u.status()),
                Map.of("status", "ACTIVE"));
        roleLookup.evictAfterCommit(userId);
        return get(userId);
    }

    @Transactional
    public AdminUserRow grantAdmin(UUID adminId, UUID userId) {
        AdminUserRow u = lock(userId);
        if (u.roles().contains(RoleName.ADMIN.name())) {
            return u;
        }
        sql.update("""
                insert into user_roles (user_id, role_id) select :id, r.id from roles r where r.name = 'ADMIN'
                on conflict do nothing
                """, new MapSqlParameterSource("id", userId));
        audit.record(adminId, "ROLE_GRANTED", "user", userId.toString(), null, Map.of("role", RoleName.ADMIN.name()));
        roleLookup.evictAfterCommit(userId);
        return get(userId);
    }

    @Transactional
    public AdminUserRow revokeAdmin(UUID adminId, UUID userId) {
        AdminUserRow u = lock(userId);
        if (userId.equals(adminId)) {
            throw new ApiException(ErrorCode.INVALID_STATE, "You can't remove your own admin access");
        }
        if (!u.roles().contains(RoleName.ADMIN.name())) {
            return u;
        }
        sql.update("delete from user_roles where user_id = :id and role_id = (select id from roles where name = 'ADMIN')",
                new MapSqlParameterSource("id", userId));
        audit.record(adminId, "ROLE_REVOKED", "user", userId.toString(), Map.of("role", RoleName.ADMIN.name()), null);
        roleLookup.evictAfterCommit(userId);
        return get(userId);
    }

    /** Only ADMIN is managed here; ORGANIZER comes from approving an application. */
    public static void requireAdminRole(String role) {
        if (!RoleName.ADMIN.name().equals(role)) {
            throw FieldErrors.single("role", "only ADMIN can be changed here");
        }
    }

    private AdminUserRow lock(UUID userId) {
        List<UUID> found = sql.queryForList("select id from users where id = :id for update",
                new MapSqlParameterSource("id", userId), UUID.class);
        if (found.isEmpty()) {
            throw ApiException.notFound("User");
        }
        return get(userId);
    }

    private AdminUserRow get(UUID userId) {
        return sql.query(SELECT + " from users u where u.id = :id", new MapSqlParameterSource("id", userId),
                (rs, n) -> row(rs)).stream().findFirst().orElseThrow(() -> ApiException.notFound("User"));
    }

    private static AdminUserRow row(ResultSet rs) throws SQLException {
        Array roles = rs.getArray("roles");
        List<String> names = roles == null ? List.of()
                : Arrays.stream((Object[]) roles.getArray()).map(String::valueOf).toList();
        return new AdminUserRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"), names,
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("suspended_at", OffsetDateTime.class), rs.getString("suspension_reason"));
    }
}
