package com.stagepass.identity;

import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class OrganizerApplicationControllerTest extends PostgresIntegrationTest {

    public static final String VALID = """
            {"orgName":"Acme Live","bio":"Gigs in Pune","contactEmail":"hello@acme.test","contactPhone":"9876543210",
             "website":"https://acme.test","pan":"ABCDE1234F","gstin":"27ABCDE1234F1Z5","state":"Maharashtra",
             "payoutAccountName":"Acme Live LLP","payoutAccountNumber":"123456789012","payoutIfsc":"HDFC0001234"}
            """;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PayoutCrypto crypto;

    private org.springframework.test.web.servlet.ResultActions apply(TestUser user, String body) throws Exception {
        return mvc.perform(post("/api/v1/organizer-applications").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void attendeeAppliesAndSeesPendingApplication() throws Exception {
        TestUser user = users.attendee();

        String id = com.jayway.jsonpath.JsonPath.read(apply(user, VALID)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payoutAccountMasked").value("••••9012"))
                .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.matchesPattern("^acme-live-[a-z0-9]{6}$")))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/organizer-applications/me").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void accountNumberIsEncryptedAtRest() throws Exception {
        TestUser user = users.attendee();
        apply(user, VALID).andExpect(status().isCreated());

        String stored = jdbc.queryForObject("select payout_account_number_enc from organizers where user_id = ?",
                String.class, user.id());
        assertThat(stored).isNotEqualTo("123456789012");
        assertThat(crypto.decrypt(stored)).isEqualTo("123456789012");
    }

    @Test
    void lowercasePanIsNormalised() throws Exception {
        TestUser user = users.attendee();
        apply(user, VALID.replace("ABCDE1234F\",\"gstin", "abcde1234f\",\"gstin").replace("HDFC0001234", "hdfc0001234"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.payoutIfsc").value("HDFC0001234"));
    }

    @Test
    void invalidPanIfscAndPhoneAreReported() throws Exception {
        String bad = VALID.replace("\"ABCDE1234F\",\"gstin", "\"123\",\"gstin")
                .replace("HDFC0001234", "SBIN123").replace("9876543210", "12345");
        apply(users.attendee(), bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("pan", "payoutIfsc", "contactPhone")));
    }

    @Test
    void secondApplicationWhilePendingIs409() throws Exception {
        TestUser user = users.attendee();
        apply(user, VALID).andExpect(status().isCreated());

        apply(user, VALID).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void rejectedApplicantCanReapply() throws Exception {
        TestUser user = users.attendee();
        String first = apply(user, VALID).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(first, "$.id");
        String slug = com.jayway.jsonpath.JsonPath.read(first, "$.slug");
        jdbc.update("update organizers set status = 'REJECTED', rejection_reason = 'PAN mismatch' where user_id = ?",
                user.id());

        apply(user, VALID.replace("Acme Live\"", "Acme Live Events\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orgName").value("Acme Live Events"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void myApplicationIs404WhenNone() throws Exception {
        mvc.perform(get("/api/v1/organizer-applications/me").header("Authorization", users.attendee().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void attendeeCannotUseOrganizerEndpoints() throws Exception {
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", users.attendee().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_APPROVED"));
    }

    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/organizer-applications").header("Authorization", users.approvedOrganizer().bearer()))
                .andExpect(status().isForbidden());
    }
}
