package com.stagepass.organizer;

import com.stagepass.organizer.service.PayoutBalances;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayoutBalancesTest extends PostgresIntegrationTest {

    @Autowired
    PayoutBalances balances;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    /** A published event that started {@code daysAgo} days ago (ends 3 hours after it starts). */
    private UUID eventStarted(TestUser org, double daysAgo) {
        return catalog.publishedEvent(org, "Payout test", OffsetDateTime.now().minusHours((long) (daysAgo * 24)), "Paycity", 100);
    }

    private void ledger(TestUser org, UUID event, String type, long amount) {
        jdbc.update("insert into organizer_ledger_entries (organizer_id, event_id, type, amount_paise) values (?, ?, ?, ?)",
                org.organizerId(), event, type, amount);
    }

    private void payout(TestUser org, long amount) {
        jdbc.update("insert into payouts (organizer_id, amount_paise, reference, paid_at, recorded_by) values (?, ?, 'UTR123', now(), ?)",
                org.organizerId(), amount, users.admin().id());
        jdbc.update("insert into organizer_ledger_entries (organizer_id, type, amount_paise, note) values (?, 'PAYOUT', ?, 'UTR123')",
                org.organizerId(), -amount);
    }

    @Test
    void earningsBecomePayableThreeDaysAfterEventEnds() {
        TestUser org = users.approvedOrganizer();
        UUID old = eventStarted(org, 4);
        UUID recent = eventStarted(org, 1);
        ledger(org, old, "SALE", 99800);
        ledger(org, recent, "SALE", 49900);

        PayoutBalances.Balances b = balances.forOrganizer(org.organizerId());

        assertThat(b.payablePaise()).isEqualTo(99800);
        assertThat(b.pendingPaise()).isEqualTo(49900);
        assertThat(b.balancePaise()).isEqualTo(149700);
        assertThat(b.negative()).isFalse();
        assertThat(b.events()).filteredOn(e -> e.eventId().equals(recent)).singleElement()
                .satisfies(e -> assertThat(e.payable()).isFalse());
    }

    @Test
    void cancelledEventIsPayableAtOnce() {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.publishedEvent(org, "Cancelled", OffsetDateTime.now().plusDays(20), "Paycity", 100);
        jdbc.update("update events set status = 'CANCELLED' where id = ?", event);
        ledger(org, event, "SALE", 99800);
        ledger(org, event, "COMMISSION", -4990);

        PayoutBalances.Balances b = balances.forOrganizer(org.organizerId());

        assertThat(b.payablePaise()).isEqualTo(94810);
        assertThat(b.pendingPaise()).isZero();
    }

    @Test
    void payoutsReducePayable() {
        TestUser org = users.approvedOrganizer();
        ledger(org, eventStarted(org, 5), "SALE", 99800);
        payout(org, 99800);

        PayoutBalances.Balances b = balances.forOrganizer(org.organizerId());

        assertThat(b.payablePaise()).isZero();
        assertThat(b.paidOutPaise()).isEqualTo(99800);
    }

    @Test
    void refundAfterPayoutGoesNegative() {
        TestUser org = users.approvedOrganizer();
        UUID event = eventStarted(org, 5);
        ledger(org, event, "SALE", 99800);
        payout(org, 99800);
        ledger(org, event, "REFUND", -49900);

        PayoutBalances.Balances b = balances.forOrganizer(org.organizerId());

        assertThat(b.payablePaise()).isEqualTo(-49900);
        assertThat(b.negative()).isTrue();
    }

    @Test
    void ledgerListsEntriesNewestFirstAndOnlyOwn() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = eventStarted(org, 5);
        ledger(org, event, "SALE", 99800);
        jdbc.update("update organizer_ledger_entries set created_at = now() - interval '1 day' where organizer_id = ?",
                org.organizerId());
        ledger(org, event, "REFUND", -49900);
        payout(org, 10000);
        TestUser other = users.approvedOrganizer();
        ledger(other, eventStarted(other, 5), "SALE", 777);

        mvc.perform(get("/api/v1/organizer/ledger").header("Authorization", org.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.payablePaise").value(39900))
                .andExpect(jsonPath("$.entries.totalItems").value(3))
                .andExpect(jsonPath("$.entries.items[2].type").value("SALE"))
                .andExpect(jsonPath("$.entries.items[2].eventTitle").value("Payout test"))
                .andExpect(jsonPath("$.entries.items[*].amountPaise", not(hasItem(777))))
                .andExpect(jsonPath("$.payouts[0].reference").value("UTR123"))
                .andExpect(jsonPath("$.payouts[0].amountPaise").value(10000));
    }
}
