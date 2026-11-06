package com.stagepass.catalog;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SchemaFixtures;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizerEventControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private String body(String title) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(30).withNano(0);
        return """
                {"title":"%s","categoryId":"%s","summary":"Three stages, one night","startsAt":"%s","endsAt":"%s"}
                """.formatted(title, catalog.musicCategoryId(), start, start.plusHours(4));
    }

    private ResultActions create(TestUser org, String json) throws Exception {
        return mvc.perform(post("/api/v1/organizer/events").header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchEvent(TestUser org, UUID id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/organizer/events/" + id).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsDraftWithSlug() throws Exception {
        create(users.approvedOrganizer(), body("Sunburn Goa 2026"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value(matchesPattern("^sunburn-goa-2026-[a-z0-9]{6}$")))
                .andExpect(jsonPath("$.categoryName").value("Music"))
                .andExpect(jsonPath("$.ticketTypes").isEmpty());
    }

    @Test
    void missingRequiredFieldsListed() throws Exception {
        create(users.approvedOrganizer(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("title", "categoryId", "startsAt", "endsAt")));
    }

    @Test
    void endBeforeStartRejected() throws Exception {
        OffsetDateTime start = OffsetDateTime.now().plusDays(5);
        create(users.approvedOrganizer(), """
                {"title":"Backwards","categoryId":"%s","startsAt":"%s","endsAt":"%s"}
                """.formatted(catalog.musicCategoryId(), start, start.minusHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endsAt"));
    }

    @Test
    void otherOrganizersVenueCannotBeUsed() throws Exception {
        UUID foreignVenue = catalog.venue(users.approvedOrganizer());
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);

        patchEvent(org, id, "{\"venueId\":\"" + foreignVenue + "\"}").andExpect(status().isNotFound());
    }

    @Test
    void otherOrganizersEventIs404ForGetPatchDelete() throws Exception {
        UUID id = catalog.draftEvent(users.approvedOrganizer());
        TestUser intruder = users.approvedOrganizer();

        mvc.perform(get("/api/v1/organizer/events/" + id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
        patchEvent(intruder, id, "{\"title\":\"Mine now\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/organizer/events/" + id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyOwnEventsNewestFirst() throws Exception {
        TestUser org = users.approvedOrganizer();
        create(org, body("First Show")).andExpect(status().isCreated());
        create(org, body("Second Show")).andExpect(status().isCreated());
        create(users.approvedOrganizer(), body("Not Mine")).andExpect(status().isCreated());

        String json = mvc.perform(get("/api/v1/organizer/events").header("Authorization", org.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> titles = JsonPath.read(json, "$.items[*].title");
        assertThat(titles).containsExactly("Second Show", "First Show");
    }

    @Test
    void listCanFilterByStatus() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID live = catalog.draftEvent(org);
        catalog.draftEvent(org);
        catalog.setStatus(live, "PUBLISHED");

        mvc.perform(get("/api/v1/organizer/events?status=PUBLISHED").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(live.toString()));
    }

    @Test
    void summaryShowsPriceAndCapacity() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);
        catalog.ticketType(id, 149900, 50);
        catalog.ticketType(id, 49900, 300);

        mvc.perform(get("/api/v1/organizer/events").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$.items[0].ticketTypeCount").value(2))
                .andExpect(jsonPath("$.items[0].minPricePaise").value(49900))
                .andExpect(jsonPath("$.items[0].totalCapacity").value(350));
    }

    @Test
    void editingPendingReviewEventReturnsItToDraft() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);
        catalog.setStatus(id, "PENDING_REVIEW");

        patchEvent(org, id, "{\"summary\":\"Updated lineup\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.summary").value("Updated lineup"));
    }

    @Test
    void reschedulingPublishedEventOpensRefundWindow() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);
        catalog.setStatus(id, "PUBLISHED");
        OffsetDateTime newStart = OffsetDateTime.now().plusDays(40).withNano(0);

        String json = patchEvent(org, id, "{\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(newStart, newStart.plusHours(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString();

        OffsetDateTime until = OffsetDateTime.parse(JsonPath.read(json, "$.rescheduleRefundUntil"));
        assertThat(until).isBetween(OffsetDateTime.now().plusDays(7).minusMinutes(5), OffsetDateTime.now().plusDays(7).plusMinutes(5));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EVENT_RESCHEDULED' and entity_id = ?",
                Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void textEditOnPublishedEventDoesNotOpenRefundWindow() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);
        catalog.setStatus(id, "PUBLISHED");

        patchEvent(org, id, "{\"description\":\"More details\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleRefundUntil").doesNotExist());
    }

    @Test
    void deleteDraftWorksButNotWithBookings() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID clean = catalog.draftEvent(org);
        mvc.perform(delete("/api/v1/organizer/events/" + clean).header("Authorization", org.bearer()))
                .andExpect(status().isNoContent());

        UUID booked = catalog.draftEvent(org);
        SchemaFixtures fx = new SchemaFixtures(jdbc);
        fx.confirmedBooking(fx.user("buyer-" + UUID.randomUUID() + "@test.in"), booked);
        mvc.perform(delete("/api/v1/organizer/events/" + booked).header("Authorization", org.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void cancelledEventIsReadOnly() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID id = catalog.draftEvent(org);
        catalog.setStatus(id, "CANCELLED");

        patchEvent(org, id, "{\"title\":\"Revived\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void invalidRefundPolicyRejected() throws Exception {
        TestUser org = users.approvedOrganizer();
        patchEvent(org, catalog.draftEvent(org), "{\"refundPolicy\":\"ALWAYS\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refundPolicy"));
    }
}
