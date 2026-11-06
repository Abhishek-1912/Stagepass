package com.stagepass.discovery;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.discovery.service.DateRanges;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicEventSearchTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    TestUser org;
    /** A city name unique to this test so rows from other tests never interfere. */
    String city;
    OffsetDateTime inTwoWeeks;

    @BeforeEach
    void setUp() {
        org = users.approvedOrganizer();
        city = "Testcity" + UUID.randomUUID().toString().substring(0, 8);
        inTwoWeeks = OffsetDateTime.now().plusDays(14).withNano(0);
    }

    private List<String> ids(String query) throws Exception {
        String json = mvc.perform(get(java.net.URI.create("/api/v1/public/events?size=100&" + query)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.items[*].id");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Test
    void onlyPublishedUpcomingEventsAreListed() throws Exception {
        UUID live = catalog.publishedEvent(org, "Live Show", inTwoWeeks, city, 49900);
        UUID draft = catalog.publishedEvent(org, "Draft Show", inTwoWeeks, city, 49900);
        UUID pending = catalog.publishedEvent(org, "Pending Show", inTwoWeeks, city, 49900);
        UUID rejected = catalog.publishedEvent(org, "Rejected Show", inTwoWeeks, city, 49900);
        UUID cancelled = catalog.publishedEvent(org, "Cancelled Show", inTwoWeeks, city, 49900);
        UUID ended = catalog.publishedEvent(org, "Ended Show", OffsetDateTime.now().minusDays(2), city, 49900);
        catalog.setStatus(draft, "DRAFT");
        catalog.setStatus(pending, "PENDING_REVIEW");
        catalog.setStatus(rejected, "REJECTED");
        catalog.setStatus(cancelled, "CANCELLED");

        assertThat(ids("city=" + city)).containsExactly(live.toString());
        assertThat(ids("q=" + enc("Draft Show"))).doesNotContain(draft.toString());
        assertThat(ids("q=Ended")).doesNotContain(ended.toString());
    }

    @Test
    void searchesTitleSummaryVenueAndCity() throws Exception {
        String token = "zq" + UUID.randomUUID().toString().substring(0, 6);
        UUID byTitle = catalog.publishedEvent(org, "Sunburn " + token, inTwoWeeks, city, 49900);
        UUID byCity = catalog.publishedEvent(org, "Other night", inTwoWeeks, city, 49900);
        jdbc.update("update events set summary = ? where id = ?", "Techno " + token + "x all night", byCity);

        assertThat(ids("q=" + token)).contains(byTitle.toString());
        assertThat(ids("q=" + city)).contains(byTitle.toString(), byCity.toString());
        assertThat(ids("q=" + city.toLowerCase())).contains(byCity.toString());
        assertThat(ids("q=" + enc("Test Arena") + "&city=" + city)).hasSize(2);
    }

    @Test
    void fuzzyTitleMatchesTypos() throws Exception {
        UUID event = catalog.publishedEvent(org, "Sunburn Arena Tour", inTwoWeeks, city, 49900);
        assertThat(ids("city=" + city + "&q=sunbrn")).contains(event.toString());
    }

    @Test
    void oddSearchInputNeverFails() throws Exception {
        for (String q : List.of("\"", "a & b", "c:d", "%", "_", "\\", "😀", "दिल्ली", "' or 1=1 --", "(((", "!:*")) {
            mvc.perform(get(java.net.URI.create("/api/v1/public/events?q=" + enc(q)))).andExpect(status().isOk());
        }
    }

    @Test
    void percentSignIsNotAWildcard() throws Exception {
        catalog.publishedEvent(org, "Plain show", inTwoWeeks, city, 49900);
        assertThat(ids("city=" + city + "&q=" + enc("%"))).isEmpty();
    }

    @Test
    void filtersByCityCategoryPriceAndFree() throws Exception {
        UUID music = catalog.publishedEvent(org, "Gig", inTwoWeeks, city, 49900);
        UUID comedy = catalog.publishedEvent(org, "Standup", inTwoWeeks, city, 79900, 12.97, 77.59, "comedy");
        UUID free = catalog.publishedEvent(org, "Meetup", inTwoWeeks, city, 0, 12.97, 77.59, "technology");

        assertThat(ids("city=" + city.toUpperCase())).containsExactlyInAnyOrder(music.toString(), comedy.toString(), free.toString());
        assertThat(ids("city=" + city + "&category=comedy")).containsExactly(comedy.toString());
        assertThat(ids("city=" + city + "&minPrice=400&maxPrice=600")).containsExactly(music.toString());
        assertThat(ids("city=" + city + "&free=true")).containsExactly(free.toString());
    }

    @Test
    void hiddenTypesDoNotSetFromPrice() throws Exception {
        UUID event = catalog.publishedEvent(org, "Tiers", inTwoWeeks, city, 99900);
        UUID hidden = catalog.ticketType(event, 9900, 10);
        jdbc.update("update ticket_types set status = 'HIDDEN' where id = ?", hidden);

        mvc.perform(get("/api/v1/public/events?city=" + city))
                .andExpect(jsonPath("$.items[0].minPricePaise").value(99900))
                .andExpect(jsonPath("$.items[0].soldOut").value(false));
    }

    @Test
    void soldOutWhenNoActiveAvailability() throws Exception {
        UUID event = catalog.publishedEvent(org, "Packed", inTwoWeeks, city, 49900);
        jdbc.update("update ticket_inventory set sold = total where ticket_type_id in (select id from ticket_types where event_id = ?)", event);

        mvc.perform(get("/api/v1/public/events?city=" + city)).andExpect(jsonPath("$.items[0].soldOut").value(true));
    }

    @Test
    void weekendFilterUsesIstEdges() throws Exception {
        DateRanges.Range weekend = DateRanges.resolve("weekend", null, null, Clock.systemUTC()).orElseThrow();
        UUID lateSunday = catalog.publishedEvent(org, "Late Sunday", weekend.end().minusMinutes(30), city, 100);
        UUID earlyMonday = catalog.publishedEvent(org, "Early Monday", weekend.end().plusMinutes(30), city, 100);

        List<String> found = ids("city=" + city + "&date=weekend");
        assertThat(found).contains(lateSunday.toString()).doesNotContain(earlyMonday.toString());
    }

    @Test
    void customDateRange() throws Exception {
        UUID event = catalog.publishedEvent(org, "Dated", OffsetDateTime.parse("2027-01-10T19:00:00+05:30"), city, 100);
        assertThat(ids("city=" + city + "&from=2027-01-10&to=2027-01-10")).containsExactly(event.toString());
        assertThat(ids("city=" + city + "&from=2027-01-11&to=2027-01-12")).isEmpty();
    }

    @Test
    void nearbySortsByDistanceAndRespectsRadius() throws Exception {
        UUID bengaluru = catalog.publishedEvent(org, "Near", inTwoWeeks, city, 100, 12.9850, 77.6050, "music");
        UUID mysuru = catalog.publishedEvent(org, "Far", inTwoWeeks, city, 100, 12.2958, 76.6394, "music");

        String json = mvc.perform(get("/api/v1/public/events?city=" + city + "&lat=12.9716&lng=77.5946&radiusKm=50&sort=nearby"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(json, "$.items[*].id")).containsExactly(bengaluru.toString());
        double km = ((Number) JsonPath.read(json, "$.items[0].distanceKm")).doubleValue();
        assertThat(km).isBetween(1.0, 3.0);
        assertThat(ids("city=" + city + "&lat=12.9716&lng=77.5946&radiusKm=200&sort=nearby"))
                .containsExactly(bengaluru.toString(), mysuru.toString());
    }

    @Test
    void nearbyWithoutCoordinatesIs400() throws Exception {
        mvc.perform(get("/api/v1/public/events?sort=nearby"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lat"));
    }

    @Test
    void invalidParametersAre400() throws Exception {
        mvc.perform(get("/api/v1/public/events?date=someday&minPrice=-1&sort=random"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void popularPutsFeaturedFirst() throws Exception {
        UUID soon = catalog.publishedEvent(org, "Soon", inTwoWeeks.minusDays(5), city, 100);
        UUID featured = catalog.publishedEvent(org, "Star", inTwoWeeks, city, 100);
        jdbc.update("update events set is_featured = true where id = ?", featured);

        assertThat(ids("city=" + city)).containsExactly(featured.toString(), soon.toString());
        assertThat(ids("city=" + city + "&sort=soonest")).containsExactly(soon.toString(), featured.toString());
    }

    @Test
    void responseIsPublicAndCacheable() throws Exception {
        mvc.perform(get("/api/v1/public/events"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")));
    }
}
