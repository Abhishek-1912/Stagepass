package com.stagepass.organizer;

import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationSettingsTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions patchProfile(TestUser org, String json) throws Exception {
        return mvc.perform(patch("/api/v1/organizer/profile").header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void updatesProfileAndMasksAccount() throws Exception {
        TestUser org = users.approvedOrganizer();
        String logo = "https://res.cloudinary.com/demo/image/upload/v17/stagepass/organizers/" + org.id() + "/logo.png";

        patchProfile(org, """
                {"bio":"Gigs since 2015","website":"https://acme.in","logoUrl":"%s","contactPhone":"9876543210",
                 "payoutAccountName":"Acme Live LLP","payoutAccountNumber":"123456781234","payoutIfsc":"hdfc0001234"}
                """.formatted(logo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Gigs since 2015"))
                .andExpect(jsonPath("$.payoutAccountMasked").value("••••1234"))
                .andExpect(jsonPath("$.payoutIfsc").value("HDFC0001234"))
                .andExpect(jsonPath("$.payoutAccountNumber").doesNotExist());

        mvc.perform(get("/api/v1/organizer/profile").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$.logoUrl").value(logo)).andExpect(jsonPath("$.website").value("https://acme.in"));
        String stored = jdbc.queryForObject("select payout_account_number_enc from organizers where id = ?", String.class,
                org.organizerId());
        assertThat(stored).isNotBlank().doesNotContain("123456781234");
        String audit = jdbc.queryForObject("select after::text from audit_logs where action = 'PAYOUT_DETAILS_CHANGED' "
                + "and entity_id = ?", String.class, org.organizerId().toString());
        assertThat(audit).contains("1234").doesNotContain("123456781234");
    }

    @Test
    void rejectsForeignLogoAndBadValues() throws Exception {
        TestUser org = users.approvedOrganizer();
        patchProfile(org, "{\"logoUrl\":\"https://res.cloudinary.com/demo/image/upload/stagepass/organizers/someone-else/l.png\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("logoUrl")));
        patchProfile(org, "{\"website\":\"ftp://acme.in\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("website")));
        patchProfile(org, "{\"contactPhone\":\"12345\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("contactPhone")));
        patchProfile(org, "{\"payoutAccountNumber\":\"123456781234\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("payoutIfsc")));
    }

    @Test
    void readOnlyFieldsIgnored() throws Exception {
        TestUser org = users.approvedOrganizer();
        String before = jdbc.queryForObject("select org_name || '|' || slug from organizers where id = ?", String.class,
                org.organizerId());

        patchProfile(org, "{\"orgName\":\"Hijack\",\"slug\":\"hijack\",\"bio\":\"x\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select org_name || '|' || slug from organizers where id = ?", String.class,
                org.organizerId())).isEqualTo(before);
    }
}
