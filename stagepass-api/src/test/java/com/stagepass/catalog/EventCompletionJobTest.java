package com.stagepass.catalog;

import com.stagepass.catalog.service.EventCompletionJob;
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

class EventCompletionJobTest extends PostgresIntegrationTest {

    @Autowired
    EventCompletionJob job;

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void completionJobCompletesEndedEvents() {
        TestUser org = users.approvedOrganizer();
        UUID ended = catalog.publishedEvent(org, "Over", OffsetDateTime.now().minusDays(1), "Jobcity", 100);
        UUID future = catalog.publishedEvent(org, "Ahead", OffsetDateTime.now().plusDays(1), "Jobcity", 100);
        jdbc.update("update events set is_featured = true where id in (?, ?)", ended, future);

        assertThat(job.completeEnded()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForMap("select status, is_featured from events where id = ?", ended))
                .containsEntry("status", "COMPLETED").containsEntry("is_featured", false);
        assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, future))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EVENT_COMPLETED' and entity_id = ?",
                Integer.class, ended.toString())).isEqualTo(1);
    }
}
