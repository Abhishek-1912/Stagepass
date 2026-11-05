package com.stagepass.identity.repository;

import com.stagepass.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Race-safe first-time insert; returns 1 if this call created the row, 0 if a row with this id
     * <em>or this email</em> already existed (callers distinguish by loading the id afterwards).
     */
    @Modifying
    @Query(value = """
            insert into users (id, email, full_name, avatar_url)
            values (:id, :email, :fullName, :avatarUrl)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("email") String email,
            @Param("fullName") String fullName, @Param("avatarUrl") String avatarUrl);

    /** Idempotent role grant; returns 1 if newly granted. */
    @Modifying
    @Query(value = """
            insert into user_roles (user_id, role_id)
            select :userId, r.id from roles r where r.name = :role
            on conflict do nothing
            """, nativeQuery = true)
    int grantRole(@Param("userId") UUID userId, @Param("role") String role);
}
