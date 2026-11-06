package com.stagepass.support;

import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.identity.service.UserProvisioningService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Creates fresh, provisioned users with roles for integration tests. Every call makes a new random user. */
@Component
public class TestUsers {

    public record TestUser(UUID id, String email, String token, UUID organizerId) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    private final UserProvisioningService provisioning;
    private final RoleLookupService roles;
    private final JdbcTemplate jdbc;

    public TestUsers(UserProvisioningService provisioning, RoleLookupService roles, JdbcTemplate jdbc) {
        this.provisioning = provisioning;
        this.roles = roles;
        this.jdbc = jdbc;
    }

    public TestUser attendee() {
        UUID id = UUID.randomUUID();
        String email = "user-" + id + "@test.in";
        provisioning.provision(id, email, "Test User", null);
        return new TestUser(id, email, TestJwt.token(id, email), null);
    }

    public TestUser admin() {
        TestUser user = attendee();
        grant(user.id(), "ADMIN");
        return user;
    }

    public TestUser approvedOrganizer() {
        return organizer("APPROVED");
    }

    public TestUser pendingOrganizer() {
        return organizer("PENDING");
    }

    private TestUser organizer(String status) {
        TestUser user = attendee();
        grant(user.id(), "ORGANIZER");
        UUID organizerId = jdbc.queryForObject("""
                insert into organizers (user_id, org_name, slug, contact_email, pan, status)
                values (?, 'Test Org', ?, ?, 'ABCDE1234F', ?) returning id
                """, UUID.class, user.id(), "test-org-" + user.id(), "org-" + user.id() + "@test.in", status);
        return new TestUser(user.id(), user.email(), user.token(), organizerId);
    }

    private void grant(UUID userId, String role) {
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = ? on conflict do nothing",
                userId, role);
        roles.evict(userId);
    }
}
