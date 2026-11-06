package com.stagepass.catalog;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VenueControllerTest extends PostgresIntegrationTest {

    static final String VENUE = """
            {"name":"NSCI Dome","addressLine":"Lala Lajpat Rai Marg, Worli","city":"Mumbai","state":"Maharashtra",
             "pincode":"400018","lat":19.0176,"lng":72.8166}
            """;

    @Autowired
    TestUsers users;

    private ResultActions create(TestUser org, String body) throws Exception {
        return mvc.perform(post("/api/v1/organizer/venues").header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void organizerCreatesAndListsOwnVenues() throws Exception {
        TestUser org = users.approvedOrganizer();
        create(org, VENUE).andExpect(status().isCreated()).andExpect(jsonPath("$.city").value("Mumbai"));
        create(org, VENUE.replace("NSCI Dome", "Antisocial")).andExpect(status().isCreated());
        create(users.approvedOrganizer(), VENUE.replace("NSCI Dome", "Someone else")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/organizer/venues").header("Authorization", org.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Antisocial"));
    }

    @Test
    void otherOrganizersVenueIs404() throws Exception {
        String id = JsonPath.read(create(users.approvedOrganizer(), VENUE).andReturn().getResponse().getContentAsString(),
                "$.id");

        mvc.perform(patch("/api/v1/organizer/venues/" + id).header("Authorization", users.approvedOrganizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanRenameVenue() throws Exception {
        TestUser org = users.approvedOrganizer();
        String id = JsonPath.read(create(org, VENUE).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/v1/organizer/venues/" + id).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Dome SVP Stadium\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dome SVP Stadium"))
                .andExpect(jsonPath("$.city").value("Mumbai"));
    }

    @Test
    void invalidPincodeAndLatitudeRejected() throws Exception {
        create(users.approvedOrganizer(), VENUE.replace("400018", "0123").replace("19.0176", "120"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("pincode", "lat")));
    }

    @Test
    void pendingOrganizerGets403NotApproved() throws Exception {
        create(users.pendingOrganizer(), VENUE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_APPROVED"));
    }
}
