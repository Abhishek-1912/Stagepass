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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketTypeControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private String base(UUID eventId) {
        return "/api/v1/organizer/events/" + eventId + "/ticket-types";
    }

    private ResultActions add(TestUser org, UUID eventId, String json) throws Exception {
        return mvc.perform(post(base(eventId)).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions change(TestUser org, UUID eventId, UUID typeId, String json) throws Exception {
        return mvc.perform(patch(base(eventId) + "/" + typeId).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID addGeneral(TestUser org, UUID eventId) throws Exception {
        return UUID.fromString(JsonPath.read(add(org, eventId, "{\"name\":\"General\",\"pricePaise\":49900,\"quantity\":300}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void addsTicketTypesWithInventory() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        addGeneral(org, event);
        add(org, event, "{\"name\":\"VIP\",\"pricePaise\":149900,\"quantity\":50,\"maxPerOrder\":4,\"sortOrder\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(50))
                .andExpect(jsonPath("$.maxPerOrder").value(4));

        mvc.perform(get("/api/v1/organizer/events/" + event).header("Authorization", org.bearer()))
                .andExpect(jsonPath("$.ticketTypes[0].name").value("General"))
                .andExpect(jsonPath("$.ticketTypes[0].available").value(300))
                .andExpect(jsonPath("$.ticketTypes[1].name").value("VIP"));
    }

    @Test
    void requiredFieldsOnCreate() throws Exception {
        TestUser org = users.approvedOrganizer();
        add(org, catalog.draftEvent(org), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "pricePaise", "quantity")));
    }

    @Test
    void quantityCannotGoBelowSoldPlusHeld() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        UUID type = addGeneral(org, event);
        jdbc.update("update ticket_inventory set sold = 40, held = 5 where ticket_type_id = ?", type);

        change(org, event, type, "{\"quantity\":30}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("45")));
        assertThat(jdbc.queryForObject("select total from ticket_inventory where ticket_type_id = ?", Integer.class, type))
                .isEqualTo(300);
    }

    @Test
    void quantityCanShrinkToExactlySoldPlusHeld() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        UUID type = addGeneral(org, event);
        jdbc.update("update ticket_inventory set sold = 40, held = 5 where ticket_type_id = ?", type);

        change(org, event, type, "{\"quantity\":45}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(45))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void priceIsLockedAfterFirstSale() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        UUID type = addGeneral(org, event);
        jdbc.update("update ticket_inventory set sold = 1 where ticket_type_id = ?", type);

        change(org, event, type, "{\"pricePaise\":59900}").andExpect(status().isConflict());
        change(org, event, type, "{\"name\":\"General Admission\"}").andExpect(status().isOk());
    }

    @Test
    void cannotDeleteTypeWithSales() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        UUID sold = addGeneral(org, event);
        UUID unsold = addGeneral(org, event);
        jdbc.update("update ticket_inventory set sold = 2 where ticket_type_id = ?", sold);

        mvc.perform(delete(base(event) + "/" + sold).header("Authorization", org.bearer())).andExpect(status().isConflict());
        mvc.perform(delete(base(event) + "/" + unsold).header("Authorization", org.bearer())).andExpect(status().isNoContent());
    }

    @Test
    void saleWindowMustEndBeforeEvent() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        java.time.OffsetDateTime afterEnd = jdbc.queryForObject("select ends_at from events where id = ?",
                java.time.OffsetDateTime.class, event).plusDays(1);

        add(org, event, "{\"name\":\"Late\",\"pricePaise\":100,\"quantity\":5,\"saleEndsAt\":\"" + afterEnd + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("saleEndsAt"));
    }

    @Test
    void maxPerOrderMustCoverMin() throws Exception {
        TestUser org = users.approvedOrganizer();
        add(org, catalog.draftEvent(org), "{\"name\":\"Pairs\",\"pricePaise\":100,\"quantity\":5,\"minPerOrder\":4,\"maxPerOrder\":2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxPerOrder"));
    }

    @Test
    void otherOrganizersTicketTypeIs404() throws Exception {
        TestUser owner = users.approvedOrganizer();
        UUID event = catalog.draftEvent(owner);
        UUID type = addGeneral(owner, event);
        TestUser intruder = users.approvedOrganizer();

        change(intruder, event, type, "{\"quantity\":1}").andExpect(status().isNotFound());
        add(intruder, event, "{\"name\":\"X\",\"pricePaise\":0,\"quantity\":1}").andExpect(status().isNotFound());
    }

    @Test
    void ticketTypeOfAnotherEventIs404() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID eventA = catalog.draftEvent(org);
        UUID eventB = catalog.draftEvent(org);
        UUID typeOfA = addGeneral(org, eventA);

        change(org, eventB, typeOfA, "{\"quantity\":10}").andExpect(status().isNotFound());
    }

    @Test
    void freeTicketTypeAllowed() throws Exception {
        TestUser org = users.approvedOrganizer();
        add(org, catalog.draftEvent(org), "{\"name\":\"RSVP\",\"pricePaise\":0,\"quantity\":100}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricePaise").value(0));
    }

    @Test
    void cancelledEventTicketTypesAreReadOnly() throws Exception {
        TestUser org = users.approvedOrganizer();
        UUID event = catalog.draftEvent(org);
        catalog.setStatus(event, "CANCELLED");

        add(org, event, "{\"name\":\"X\",\"pricePaise\":0,\"quantity\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
