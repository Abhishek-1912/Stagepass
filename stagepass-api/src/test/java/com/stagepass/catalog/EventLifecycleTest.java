package com.stagepass.catalog;

import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventLifecycleTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    /** A draft that passes every submit check. */
    private UUID completeDraft(TestUser org) {
        UUID event = catalog.draftEvent(org);
        jdbc.update("update events set venue_id = ?, summary = 'A night to remember', cover_image_url = ? where id = ?",
                catalog.venue(org), "https://res.cloudinary.com/demo/image/upload/stagepass/events/" + event + "/cover.jpg",
                event);
        catalog.ticketType(event, 49900, 100);
        return event;
    }

    private ResultActions submit(TestUser org, UUID event) throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/" + event + "/submit").header("Authorization", org.bearer()));
    }

    private ResultActions admin(String action, UUID event, String body) throws Exception {
        var req = post("/api/v1/admin/events/" + event + "/" + action).header("Authorization", users.admin().bearer());
        return mvc.perform(body == null ? req : req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void incompleteEventListsEveryProblem() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        jdbc.update("update events set starts_at = now() - interval '1 day', ends_at = now() where id = ?", event);

        submit(org, event)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("coverImageUrl", "ticketTypes", "startsAt", "venueId", "summary")));
        assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, event)).isEqualTo("DRAFT");
    }

    @Test
    void onlineEventNeedsLinkInsteadOfVenue() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        jdbc.update("update events set venue_id = null, is_online = true where id = ?", event);

        submit(org, event).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("onlineUrl")));
    }

    @Test
    void completeEventCanBeSubmitted() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);

        submit(org, event)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
        submit(org, event).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void adminSeesQueueAndApprovesAndOrganizerIsEmailed() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        submit(org, event).andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/events?status=PENDING_REVIEW&size=100").header("Authorization", users.admin().bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(event.toString())));

        admin("approve", event, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("""
                select count(*) from email_outbox o join organizers g on g.contact_email = o.to_address
                where g.id = ? and o.template = 'EVENT_APPROVED'
                """, Integer.class, org.organizerId())).isEqualTo(1);
    }

    @Test
    void adminRejectsWithReasonAndOrganizerCanResubmit() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        submit(org, event);

        admin("reject", event, "{\"reason\":\"Add venue photos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Add venue photos"));

        mvc.perform(patch("/api/v1/organizer/events/" + event).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Now with photos\"}"))
                .andExpect(status().isOk());
        submit(org, event)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void cannotApproveEventThatAlreadyStarted() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        submit(org, event);
        jdbc.update("update events set starts_at = now() - interval '1 hour' where id = ?", event);

        admin("approve", event, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void cannotApproveDraft() throws Exception {
        admin("approve", completeDraft(users.approvedOrganizer()), null).andExpect(status().isConflict());
    }

    @Test
    void onlyPublishedEventsCanBeFeatured() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);

        admin("feature", event, "{\"featured\":true}").andExpect(status().isConflict());
        catalog.setStatus(event, "PUBLISHED");
        admin("feature", event, "{\"featured\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(true));
    }

    @Test
    void organizerCannotApproveOwnEvent() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        submit(org, event);

        mvc.perform(post("/api/v1/admin/events/" + event + "/approve").header("Authorization", org.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelPublishedEvent() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = completeDraft(org);
        catalog.setStatus(event, "PUBLISHED");

        mvc.perform(post("/api/v1/organizer/events/" + event + "/cancel").header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Artist unwell\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'EVENT_CANCELLED' and entity_id = ?",
                Integer.class, event.toString())).isEqualTo(1);
    }

    @Test
    void otherOrganizerCannotSubmitOrCancel() throws Exception {
        UUID event = completeDraft(users.approvedOrganizer());
        TestUser intruder = users.approvedOrganizer();

        submit(intruder, event).andExpect(status().isNotFound());
    }
}
