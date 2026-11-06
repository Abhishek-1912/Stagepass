package com.stagepass.discovery;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the Plan 3 final-review findings. */
class DiscoveryReviewFixesTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    TestUser org;
    String city;
    OffsetDateTime soon;

    @BeforeEach
    void setUp() {
        org = users.approvedOrganizer();
        city = "Fixcity" + UUID.randomUUID().toString().substring(0, 8);
        soon = OffsetDateTime.now().plusDays(9).withNano(0);
    }

    private String json(String path) throws Exception {
        return mvc.perform(get(URI.create(path))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void suspendedOrganizersEventsAreNotListedAnywhere() throws Exception {
        UUID event = catalog.publishedEvent(org, "Suspended show", soon, city, 100);
        jdbc.update("update organizers set status = 'SUSPENDED' where id = ?", org.organizerId());

        assertThat((List<String>) JsonPath.read(json("/api/v1/public/events?city=" + city), "$.items[*].id")).isEmpty();
        assertThat((List<String>) JsonPath.read(json("/api/v1/public/home?city=" + city), "$.upcoming[*].id"))
                .doesNotContain(event.toString());
        List<Map<String, Object>> cities = JsonPath.read(json("/api/v1/public/cities"), "$");
        assertThat(cities).noneSatisfy(c -> assertThat(c.get("name")).isEqualTo(city));
    }

    @Test
    void trendingIsOrderedByPopularityNotFeatured() throws Exception {
        UUID featured = catalog.publishedEvent(org, "Featured but quiet", soon, city, 100);
        UUID popular = catalog.publishedEvent(org, "Popular", soon.plusDays(1), city, 100);
        jdbc.update("update events set is_featured = true where id = ?", featured);
        jdbc.update("update events set popularity_score = 50 where id = ?", popular);

        List<String> trending = JsonPath.read(json("/api/v1/public/home?city=" + city), "$.trending[*].id");
        assertThat(trending.indexOf(popular.toString())).isNotNegative().isLessThan(trending.indexOf(featured.toString()));
    }

    @Test
    void cardIsSoldOutWhenEverySaleHasEnded() throws Exception {
        UUID event = catalog.publishedEvent(org, "Sales closed", soon, city, 100);
        jdbc.update("update ticket_types set sale_ends_at = now() - interval '1 hour' where event_id = ?", event);

        mvc.perform(get("/api/v1/public/events?city=" + city)).andExpect(jsonPath("$.items[0].soldOut").value(true));
    }

    @Test
    void nonFiniteCoordinatesAreRejected() throws Exception {
        mvc.perform(get("/api/v1/public/home?lat=NaN&lng=NaN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/home?lat=Infinity&lng=1")).andExpect(status().isBadRequest());
    }

    @Test
    void organizerProfileReportsTotalUpcomingEvents() throws Exception {
        for (int i = 0; i < 26; i++) {
            catalog.publishedEvent(org, "Show " + i, soon.plusHours(i), city, 100);
        }
        String slug = jdbc.queryForObject("select slug from organizers where id = ?", String.class, org.organizerId());

        mvc.perform(get("/api/v1/public/organizers/" + slug))
                .andExpect(jsonPath("$.upcomingCount").value(26))
                .andExpect(jsonPath("$.upcoming.length()").value(24));
    }
}
