package com.stagepass.booking;

import com.stagepass.booking.service.FeeSettings;
import com.stagepass.booking.service.FeeSettingsRepository;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeeSettingsRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    FeeSettingsRepository fees;

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void platformDefaultsComeFromSettings() {
        assertThat(fees.current()).isEqualTo(new FeeSettings(250, 1000, 1800, 0, false));
    }

    @Test
    void eventOverrideBeatsPlatformDefault() {
        UUID event = catalog.draftEvent(users.approvedOrganizer());
        jdbc.update("update events set convenience_fee_override = '{\"bps\":100,\"flatPaise\":0}'::jsonb where id = ?", event);

        FeeSettings s = fees.forEvent(event);
        assertThat(s.convenienceFeeBps()).isEqualTo(100);
        assertThat(s.convenienceFeeFlatPaise()).isZero();
        assertThat(s.gstBps()).isEqualTo(1800);
    }

    @Test
    void organizerCommissionOverrideApplies() {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        jdbc.update("update organizers set commission_bps_override = 300 where id = ?", org.organizerId());
        assertThat(fees.forEvent(event).commissionBps()).isEqualTo(300);

        jdbc.update("update events set commission_bps_override = 150 where id = ?", event);
        assertThat(fees.forEvent(event).commissionBps()).isEqualTo(150);
    }
}
