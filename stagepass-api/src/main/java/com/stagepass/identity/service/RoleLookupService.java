package com.stagepass.identity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.domain.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Roles and status per user, cached for 60 seconds so authorization does not hit the database on every request.
 * Call {@link #evict(UUID)} after changing a user's roles or status.
 */
@Service
public class RoleLookupService {

    public record AccountSnapshot(UserStatus status, Set<RoleName> roles) {
    }

    private final JdbcTemplate jdbc;
    private final Cache<UUID, AccountSnapshot> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(50_000)
            .build();

    public RoleLookupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty if the user has never been provisioned. */
    public Optional<AccountSnapshot> snapshot(UUID userId) {
        // Atomic per key: an eviction during a load waits for it and then removes it, so a stale load can't stick.
        // A null result (unknown user) is not cached.
        return Optional.ofNullable(cache.get(userId, id -> load(id).orElse(null)));
    }

    public Set<RoleName> rolesFor(UUID userId) {
        return snapshot(userId).map(AccountSnapshot::roles).orElse(Set.of());
    }

    public void evict(UUID userId) {
        cache.invalidate(userId);
    }

    /**
     * Evicts once the current transaction commits (and immediately if there is none), so a concurrent request
     * cannot re-cache the pre-change roles between the eviction and the commit.
     */
    public void evictAfterCommit(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidate(userId);
                }
            });
        }
        cache.invalidate(userId);
    }

    /** Reads roles and status from the database; overridable for tests. */
    protected Optional<AccountSnapshot> load(UUID userId) {
        var rows = jdbc.queryForList("""
                select u.status, r.name as role
                from users u
                left join user_roles ur on ur.user_id = u.id
                left join roles r on r.id = ur.role_id
                where u.id = ?
                """, userId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserStatus status = UserStatus.valueOf((String) rows.get(0).get("status"));
        Set<RoleName> roles = EnumSet.noneOf(RoleName.class);
        rows.forEach(r -> {
            if (r.get("role") != null) {
                roles.add(RoleName.valueOf((String) r.get("role")));
            }
        });
        return Optional.of(new AccountSnapshot(status, Set.copyOf(roles)));
    }
}
