package com.stagepass.identity.service;

import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.config.StagepassProperties;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.identity.domain.RoleName;
import com.stagepass.identity.domain.User;
import com.stagepass.identity.repository.UserRepository;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Creates the local user row the first time a Supabase user calls the API. */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final AuditLogService audit;
    private final Set<String> bootstrapAdmins;
    private final EmailOutbox outbox;

    public UserProvisioningService(UserRepository users, AuditLogService audit, StagepassProperties props,
            EmailOutbox outbox) {
        this.users = users;
        this.audit = audit;
        this.outbox = outbox;
        this.bootstrapAdmins = props.bootstrap().adminEmails().stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public User provision(UUID id, String email, String fullName, String avatarUrl) {
        int created = users.insertIfAbsent(id, email, fullName, avatarUrl);
        if (created == 1) {
            users.grantRole(id, RoleName.ATTENDEE.name());
            if (email != null) {
                outbox.enqueue(email, EmailTemplate.WELCOME, fullName == null ? Map.of() : Map.of("name", fullName));
            }
            if (email != null && bootstrapAdmins.contains(email.toLowerCase(Locale.ROOT))
                    && users.grantRole(id, RoleName.ADMIN.name()) == 1) {
                audit.record(null, "ROLE_GRANTED", "user", id.toString(), null,
                        Map.of("role", RoleName.ADMIN.name(), "reason", "bootstrap-admin-email"));
            }
        }
        // Nothing inserted and no row with this id: the email belongs to a different account.
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_CONFLICT,
                "Another Stagepass account already uses this email. Contact support to merge accounts."));
    }
}
