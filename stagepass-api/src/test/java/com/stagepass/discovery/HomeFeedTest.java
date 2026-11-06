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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeFeedTest extends PostgresIntegrationTest {

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
        city = "Homecity" + UUID.randomUUID().toString().substring(0, 8);
        soon = OffsetDateTime.now().plusDays(10).withNano(0);
    }

    private String home(String query) throws Exception {
        return mvc.perform(get(URI.create("/api/v1/public/home?" + query)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<String> ids(String json, String feed) {
        return JsonPath.read(json, "$." + feed + "[*].id");
    }

    @Test
    void homeShowsFeaturedAndUpcoming() throws Exception {
        UUID featured = catalog.publishedEvent(org, "Star night", soon, city, 49900);
        UUID normal = catalog.publishedEvent(org, "Regular night", soon.plusDays(1), city, 49900);
        jdbc.update("update events set is_featured = true where id = ?", featured);

        String json = home("city=" + city);

        // Online events from other tests may also appear (city feeds include online events).
        assertThat(ids(json, "featured")).contains(featured.toString()).doesNotContain(normal.toString());
        List<String> upcoming = ids(json, "upcoming");
        assertThat(upcoming.indexOf(featured.toString())).isNotNegative().isLessThan(upcoming.indexOf(normal.toString()));
        assertThat(ids(json, "trending")).contains(normal.toString());
    }

    @Test
    void cityLimitsFeedsButKeepsOnlineEvents() throws Exception {
        UUID local = catalog.publishedEvent(org, "Local", soon, city, 100);
        UUID elsewhere = catalog.publishedEvent(org, "Elsewhere", soon, city + "x", 100);
        UUID online = catalog.publishedEvent(org, "Webinar", soon, city + "y", 0);
        jdbc.update("update events set venue_id = null, is_online = true, online_url = 'https://meet.example/x' where id = ?",
                online);

        List<String> upcoming = ids(home("city=" + city), "upcoming");

        assertThat(upcoming).contains(local.toString(), online.toString()).doesNotContain(elsewhere.toString());
    }

    @Test
    void nearbyUsesCoordinatesThenCity() throws Exception {
        UUID near = catalog.publishedEvent(org, "Near", soon, city, 100, 18.5204, 73.8567, "music");
        UUID online = catalog.publishedEvent(org, "Online", soon, city, 100, 18.5204, 73.8567, "music");
        jdbc.update("update events set is_online = true, online_url = 'https://x.test' where id = ?", online);

        assertThat(ids(home("lat=18.52&lng=73.85"), "nearby")).contains(near.toString()).doesNotContain(online.toString());
        assertThat(ids(home("city=" + city), "nearby")).contains(near.toString()).doesNotContain(online.toString());
        assertThat(ids(home(""), "nearby")).isEmpty();
    }

    @Test
    void emptyCategoriesAreOmitted() throws Exception {
        catalog.publishedEvent(org, "Laughs", soon, city, 100, 12.97, 77.59, "comedy");

        List<Map<String, Object>> rails = JsonPath.read(home("city=" + city), "$.categories");
        assertThat(rails).extracting(r -> r.get("slug")).contains("comedy");
        assertThat(rails).allSatisfy(r -> assertThat((List<?>) r.get("events")).isNotEmpty());
        assertThat(rails).hasSizeLessThanOrEqualTo(6);
    }

    @Test
    void citiesListedByEventCount() throws Exception {
        catalog.publishedEvent(org, "One", soon, city, 100);
        catalog.publishedEvent(org, "Two", soon, city, 100);

        String json = mvc.perform(get("/api/v1/public/cities")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> cities = JsonPath.read(json, "$");
        assertThat(cities).anySatisfy(c -> {
            assertThat(c.get("name")).isEqualTo(city);
            assertThat(c.get("upcomingEvents")).isEqualTo(2);
        });
    }
}
