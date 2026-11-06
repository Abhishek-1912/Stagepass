package com.stagepass.discovery;

import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicEventDetailTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    TestUser org;
    String city;

    @BeforeEach
    void setUp() {
        org = users.approvedOrganizer();
        city = "Detailcity" + UUID.randomUUID().toString().substring(0, 6);
    }

    private UUID event() {
        return catalog.publishedEvent(org, "Detail Night", OffsetDateTime.now().plusDays(10), city, 49900);
    }

    private String slug(UUID id) {
        return jdbc.queryForObject("select slug from events where id = ?", String.class, id);
    }

    private UUID addType(UUID event, String name, String status) {
        UUID id = catalog.ticketType(event, 19900, 100);
        jdbc.update("update ticket_types set name = ?, status = ? where id = ?", name, status, id);
        return id;
    }

    @Test
    void publishedEventIsPublic() throws Exception {
        UUID id = event();
        mvc.perform(get("/api/v1/public/events/" + slug(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Detail Night"))
                .andExpect(jsonPath("$.organizer.name").value("Test Org"))
                .andExpect(jsonPath("$.venue.city").value(city))
                .andExpect(jsonPath("$.minPricePaise").value(49900))
                .andExpect(jsonPath("$.ticketTypes", hasSize(1)))
                .andExpect(jsonPath("$.ticketTypes[0].availability").value("ON_SALE"));
    }

    @Test
    void draftPendingRejectedAre404() throws Exception {
        for (String status : new String[] {"DRAFT", "PENDING_REVIEW", "REJECTED"}) {
            UUID id = event();
            catalog.setStatus(id, status);
            mvc.perform(get("/api/v1/public/events/" + slug(id))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/events/no-such-event")).andExpect(status().isNotFound());
    }

    @Test
    void cancelledEventStillLoadsWithStatus() throws Exception {
        UUID id = event();
        catalog.setStatus(id, "CANCELLED");
        mvc.perform(get("/api/v1/public/events/" + slug(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void hiddenTicketTypesAreNotExposed() throws Exception {
        UUID id = event();
        addType(id, "Secret Guest List", "HIDDEN");
        mvc.perform(get("/api/v1/public/events/" + slug(id)))
                .andExpect(jsonPath("$.ticketTypes[*].name", not(hasItem("Secret Guest List"))))
                .andExpect(jsonPath("$.minPricePaise").value(49900));
    }

    @Test
    void availabilityStates() throws Exception {
        UUID id = event();
        UUID soldOut = addType(id, "Sold", "ACTIVE");
        jdbc.update("update ticket_inventory set sold = total where ticket_type_id = ?", soldOut);
        UUID later = addType(id, "Later", "ACTIVE");
        jdbc.update("update ticket_types set sale_starts_at = now() + interval '2 days' where id = ?", later);
        UUID ended = addType(id, "Ended", "ACTIVE");
        jdbc.update("update ticket_types set sale_ends_at = now() - interval '1 hour' where id = ?", ended);
        UUID few = addType(id, "Few", "ACTIVE");
        jdbc.update("update ticket_inventory set sold = 95 where ticket_type_id = ?", few);

        String base = "$.ticketTypes[?(@.name=='%s')].";
        mvc.perform(get("/api/v1/public/events/" + slug(id)))
                .andExpect(jsonPath(base.formatted("Sold") + "availability").value("SOLD_OUT"))
                .andExpect(jsonPath(base.formatted("Later") + "availability").value("NOT_STARTED"))
                .andExpect(jsonPath(base.formatted("Ended") + "availability").value("ENDED"))
                .andExpect(jsonPath(base.formatted("Few") + "fewLeft").value(true))
                .andExpect(jsonPath("$.ticketTypes[0].available").doesNotExist())
                .andExpect(jsonPath("$.ticketTypes[0].sold").doesNotExist());
    }

    @Test
    void onlineLinkIsNeverPublic() throws Exception {
        UUID id = event();
        jdbc.update("update events set is_online = true, online_url = 'https://meet.example/secret' where id = ?", id);
        mvc.perform(get("/api/v1/public/events/" + slug(id)))
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(content().string(not(containsString("meet.example"))));
    }

    @Test
    void organizerProfileShowsUpcomingOnlyWhenApproved() throws Exception {
        UUID id = event();
        String orgSlug = jdbc.queryForObject("select slug from organizers where id = ?", String.class, org.organizerId());

        mvc.perform(get("/api/v1/public/organizers/" + orgSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizer.name").value("Test Org"))
                .andExpect(jsonPath("$.upcoming[*].id", hasItem(id.toString())));

        jdbc.update("update organizers set status = 'SUSPENDED' where id = ?", org.organizerId());
        mvc.perform(get("/api/v1/public/organizers/" + orgSlug)).andExpect(status().isNotFound());
    }
}
