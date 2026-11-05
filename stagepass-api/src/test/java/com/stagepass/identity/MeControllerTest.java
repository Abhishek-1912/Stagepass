package com.stagepass.identity;

import com.stagepass.identity.service.RoleLookupService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RoleLookupService roleLookup;

    private static String email() {
        return "u-" + UUID.randomUUID() + "@test.in";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void meProvisionsAttendee() throws Exception {
        UUID id = UUID.randomUUID();
        String email = email();
        String token = TestJwt.builder(id, email).fullName("Asha Rao").build();

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.roles", contains("ATTENDEE")));
    }

    @Test
    void bootstrapEmailGetsAdmin() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(TestJwt.token(id, "Admin@Stagepass.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ATTENDEE", "ADMIN")));

        Integer audits = jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'ROLE_GRANTED' and entity_id = ?",
                Integer.class, id.toString());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void noTokenIs401Problem() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), email()).expiresAt(Instant.now().minusSeconds(600)).build();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void wrongIssuerIs401() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), email()).issuer("https://evil.example/auth/v1").build();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), email()).audience("anon").build();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void foreignSignatureIs401() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), email()).signedWithOtherKey().build();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedUserIs403() throws Exception {
        UUID id = UUID.randomUUID();
        String token = TestJwt.token(id, email());
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token))).andExpect(status().isOk());

        jdbc.update("update users set status = 'SUSPENDED' where id = ?", id);
        roleLookup.evict(id);

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void sameEmailNewIdIs409() throws Exception {
        String email = email();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(TestJwt.token(UUID.randomUUID(), email))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/me").header("Authorization", bearer(TestJwt.token(UUID.randomUUID(), email))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"));
    }

    @Test
    void patchMeUpdatesProfile() throws Exception {
        UUID id = UUID.randomUUID();
        String token = TestJwt.token(id, email());
        mvc.perform(patch("/api/v1/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9876543210\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha\",\"city\":\"Bengaluru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha"))
                .andExpect(jsonPath("$.city").value("Bengaluru"))
                .andExpect(jsonPath("$.phone").value("9876543210"));
    }

    @Test
    void patchMeRejectsBadPhone() throws Exception {
        String token = TestJwt.token(UUID.randomUUID(), email());
        mvc.perform(patch("/api/v1/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void rolesComeFromDbNotToken() throws Exception {
        String token = TestJwt.builder(UUID.randomUUID(), email())
                .claim("app_metadata", java.util.Map.of("role", "ADMIN"))
                .claim("roles", java.util.List.of("ADMIN"))
                .build();
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ATTENDEE")));
    }

    @Test
    void tokenWithoutEmailIsAccepted() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(TestJwt.token(UUID.randomUUID(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ATTENDEE")));
    }
}
