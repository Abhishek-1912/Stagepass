package com.stagepass.admin;

import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPayoutTest extends PostgresIntegrationTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    /** An organizer with {@code paise} payable (sale on an event that ended 5 days ago). */
    TestUser payableOrganizer(long paise) {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.publishedEvent(org, "Paid gig", OffsetDateTime.now().minusDays(5), "Paycity", 100);
        jdbc.update("insert into organizer_ledger_entries (organizer_id, event_id, type, amount_paise) values (?, ?, 'SALE', ?)",
                org.organizerId(), event, paise);
        return org;
    }

    private ResultActions record(TestUser admin, UUID organizerId, long amount, String reference, LocalDate paidOn)
            throws Exception {
        return mvc.perform(post("/api/v1/admin/organizers/" + organizerId + "/payouts").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"amountPaise":%d,"reference":"%s","paidOn":"%s","note":"NEFT"}""".formatted(amount, reference, paidOn)));
    }

    private static LocalDate today() {
        return LocalDate.now(IST);
    }

    @Test
    void recordPayoutReducesPayableAndWritesLedger() throws Exception {
        TestUser admin = users.admin();
        TestUser org = payableOrganizer(50_000);
        String contact = jdbc.queryForObject("select contact_email from organizers where id = ?", String.class, org.organizerId());

        record(admin, org.organizerId(), 30_000, " UTR123 ", today())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPaise").value(30_000))
                .andExpect(jsonPath("$.reference").value("UTR123"))
                .andExpect(jsonPath("$.recordedByEmail").value(admin.email()));

        mvc.perform(get("/api/v1/admin/organizers/" + org.organizerId()).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.balances.payablePaise").value(20_000))
                .andExpect(jsonPath("$.balances.paidOutPaise").value(30_000))
                .andExpect(jsonPath("$.payouts[0].reference").value("UTR123"));
        assertThat(jdbc.queryForObject("select amount_paise from organizer_ledger_entries where organizer_id = ? and type = 'PAYOUT'"
                + " and note = 'Payout UTR123'", Long.class, org.organizerId())).isEqualTo(-30_000L);
        mvc.perform(get("/api/v1/organizer/ledger").header("Authorization", org.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$..note", hasItem("Payout UTR123")));
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where to_address = ? and template = 'PAYOUT_RECORDED'",
                Integer.class, contact)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'PAYOUT_RECORDED' and entity_id = ?",
                Integer.class, org.organizerId().toString())).isEqualTo(1);
    }

    @Test
    void cannotPayMoreThanPayableOrPendingMoney() throws Exception {
        TestUser admin = users.admin();
        TestUser org = payableOrganizer(50_000);
        record(admin, org.organizerId(), 60_000, "UTR-BIG", today())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amountPaise"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Rs. 500.00")));

        TestUser fresh = users.approvedOrganizer();
        UUID upcoming = catalog.publishedEvent(fresh, "Soon", OffsetDateTime.now().plusDays(3), "Paycity", 100);
        jdbc.update("insert into organizer_ledger_entries (organizer_id, event_id, type, amount_paise) values (?, ?, 'SALE', 40000)",
                fresh.organizerId(), upcoming);
        record(admin, fresh.organizerId(), 100, "UTR-EARLY", today())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountPaise"));
        record(admin, org.organizerId(), 0, "UTR-ZERO", today()).andExpect(status().isBadRequest());
        record(admin, UUID.randomUUID(), 100, "UTR-NONE", today()).andExpect(status().isNotFound());
    }

    @Test
    void referenceUniquePerOrganizerCaseInsensitive() throws Exception {
        TestUser admin = users.admin();
        TestUser org = payableOrganizer(50_000);
        TestUser other = payableOrganizer(50_000);
        record(admin, org.organizerId(), 10_000, "UTR777", today()).andExpect(status().isCreated());
        record(admin, org.organizerId(), 10_000, "utr777", today())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reference"))
                .andExpect(jsonPath("$.errors[0].message").value("was already recorded"));
        record(admin, other.organizerId(), 10_000, "UTR777", today()).andExpect(status().isCreated());
        record(admin, org.organizerId(), 10_000, "ab", today())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reference"));
    }

    @Test
    void futurePaidOnRejected() throws Exception {
        TestUser admin = users.admin();
        TestUser org = payableOrganizer(50_000);
        record(admin, org.organizerId(), 10_000, "UTR-TMRW", today().plusDays(1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("paidOn"));
        record(admin, org.organizerId(), 10_000, "UTR-PAST", today().minusDays(2)).andExpect(status().isCreated());
    }

    @Test
    void summariesPayableFirstAndFilter() throws Exception {
        TestUser admin = users.admin();
        TestUser rich = payableOrganizer(90_000);
        TestUser broke = users.approvedOrganizer();
        TestUser negative = payableOrganizer(10_000);
        jdbc.update("insert into organizer_ledger_entries (organizer_id, type, amount_paise, note) values (?, 'PAYOUT', -30000, 'old')",
                negative.organizerId());
        String tag = "Payout " + UUID.randomUUID().toString().substring(0, 8);
        for (TestUser o : new TestUser[] {rich, broke, negative}) {
            jdbc.update("update organizers set org_name = ? || ' ' || substr(id::text, 1, 4) where id = ?", tag, o.organizerId());
        }

        mvc.perform(get("/api/v1/admin/payouts").param("onlyPayable", "true").param("q", tag)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].organizerId", hasItem(rich.organizerId().toString())))
                .andExpect(jsonPath("$.items[*].organizerId", not(hasItem(broke.organizerId().toString()))))
                .andExpect(jsonPath("$.items[*].organizerId", not(hasItem(negative.organizerId().toString()))));
        mvc.perform(get("/api/v1/admin/payouts").param("q", tag).header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[0].organizerId").value(rich.organizerId().toString()))
                .andExpect(jsonPath("$.items[0].payablePaise").value(90_000))
                .andExpect(jsonPath("$.items[2].organizerId").value(negative.organizerId().toString()))
                .andExpect(jsonPath("$.items[?(@.organizerId == '" + negative.organizerId() + "')].negative").value(hasItem(true)));
        String json = mvc.perform(get("/api/v1/admin/payouts").param("size", "100").header("Authorization", admin.bearer()))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> payable = com.jayway.jsonpath.JsonPath.read(json, "$.items[*].payablePaise");
        for (int i = 1; i < payable.size(); i++) {
            assertThat(payable.get(i - 1).longValue()).isGreaterThanOrEqualTo(payable.get(i).longValue());
        }
        mvc.perform(get("/api/v1/admin/payouts").header("Authorization", rich.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void shortReferenceAfterTrimIs400() throws Exception {
        TestUser admin = users.admin();
        TestUser org = payableOrganizer(50_000);
        record(admin, org.organizerId(), 10_000, "  ab  ", today())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reference"));
    }
}
