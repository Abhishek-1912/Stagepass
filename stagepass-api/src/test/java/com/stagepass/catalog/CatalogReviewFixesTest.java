package com.stagepass.catalog;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the Plan 2 final-review findings. */
class CatalogReviewFixesTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions patchEvent(TestUser org, UUID id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/organizer/events/" + id).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String eventStatus(UUID event) {
        return jdbc.queryForObject("select status from events where id = ?", String.class, event);
    }

    private UUID completePublished(TestUser org) {
        UUID event = catalog.draftEvent(org);
        jdbc.update("update events set venue_id = ?, summary = 's', cover_image_url = ?, status = 'PUBLISHED' where id = ?",
                catalog.venue(org), "https://res.cloudinary.com/demo/image/upload/stagepass/events/" + event + "/c.jpg", event);
        return event;
    }

    // Finding 1: clearing optional fields
    @Test
    void clearsOptionalEventFields() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        patchEvent(org, event, "{\"maxTicketsPerUser\":4,\"terms\":\"No re-entry\"}").andExpect(status().isOk());

        patchEvent(org, event, "{\"clear\":[\"maxTicketsPerUser\",\"terms\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTicketsPerUser").doesNotExist())
                .andExpect(jsonPath("$.terms").doesNotExist());
    }

    @Test
    void clearingARequiredFieldIsRejected() throws Exception {
        TestUser org = users.approvedOrganizer();
        patchEvent(org, catalog.draftEvent(org), "{\"clear\":[\"title\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("clear"));
    }

    @Test
    void clearsTicketTypeSaleWindow() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        OffsetDateTime end = OffsetDateTime.now().plusDays(20).withNano(0);
        String id = JsonPath.read(mvc.perform(post("/api/v1/organizer/events/" + event + "/ticket-types")
                        .header("Authorization", org.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Early\",\"pricePaise\":100,\"quantity\":5,\"saleEndsAt\":\"" + end + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/v1/organizer/events/" + event + "/ticket-types/" + id).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clear\":[\"saleEndsAt\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleEndsAt").doesNotExist());
    }

    // Finding 3: ticket changes after submit, approval re-check
    @Test
    void ticketTypeChangeReturnsPendingEventToDraft() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        catalog.setStatus(event, "PENDING_REVIEW");

        mvc.perform(post("/api/v1/organizer/events/" + event + "/ticket-types").header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"VIP\",\"pricePaise\":100,\"quantity\":5}"))
                .andExpect(status().isCreated());

        assertThat(eventStatus(event)).isEqualTo("DRAFT");
    }

    @Test
    void approvalRechecksSubmitRules() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        catalog.setStatus(event, "PENDING_REVIEW");

        mvc.perform(post("/api/v1/admin/events/" + event + "/approve").header("Authorization", users.admin().bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("ticketTypes", "coverImageUrl")));
        assertThat(eventStatus(event)).isEqualTo("PENDING_REVIEW");
    }

    // Finding 6: refund policy needs a cutoff
    @Test
    void refundPolicyNeedsCutoffHours() throws Exception {
        TestUser org = users.approvedOrganizer();
        patchEvent(org, catalog.draftEvent(org), "{\"refundPolicy\":\"UNTIL_HOURS_BEFORE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("refundCutoffHours"));
    }

    // Finding 7: venue edits on live events
    @Test
    void venueAddressChangeOnLiveEventOpensRefundWindow() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completePublished(org);
        UUID venue = jdbc.queryForObject("select venue_id from events where id = ?", UUID.class, event);

        mvc.perform(patch("/api/v1/organizer/venues/" + venue).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressLine\":\"99 New Road\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select reschedule_refund_until from events where id = ?", OffsetDateTime.class, event))
                .isAfter(OffsetDateTime.now().plusDays(6));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EVENT_RESCHEDULED' and entity_id = ?",
                Integer.class, event.toString())).isEqualTo(1);
    }

    @Test
    void venueRenameDoesNotOpenRefundWindow() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completePublished(org);
        UUID venue = jdbc.queryForObject("select venue_id from events where id = ?", UUID.class, event);

        mvc.perform(patch("/api/v1/organizer/venues/" + venue).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Arena (renamed)\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select reschedule_refund_until from events where id = ?", OffsetDateTime.class, event))
                .isNull();
    }

    // Finding 8: live events keep tickets and a future start
    @Test
    void liveEventCannotLoseItsLastActiveTicketType() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completePublished(org);
        UUID type = catalog.ticketType(event, 49900, 100);
        String base = "/api/v1/organizer/events/" + event + "/ticket-types/" + type;

        mvc.perform(delete(base).header("Authorization", org.bearer())).andExpect(status().isConflict());
        mvc.perform(patch(base).header("Authorization", org.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"HIDDEN\"}")).andExpect(status().isConflict());
    }

    @Test
    void liveEventCannotMoveIntoThePast() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completePublished(org);
        OffsetDateTime past = OffsetDateTime.now().minusDays(1).withNano(0);

        patchEvent(org, event, "{\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(past, past.plusHours(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startsAt"));
    }
}
