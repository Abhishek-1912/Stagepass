package com.stagepass.checkin;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInStatsTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    private UUID gate(TestUser org, UUID eventId, String name) throws Exception {
        String json = mvc.perform(post("/api/v1/organizer/events/{id}/gates", eventId).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions scan(TestUser user, UUID eventId, UUID gateId, String token) throws Exception {
        return mvc.perform(post("/api/v1/checkin/scan").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"%s\",\"gateId\":\"%s\",\"token\":\"%s\"}".formatted(eventId, gateId, token)));
    }

    private TestUser staff(TestUser org) throws Exception {
        String address = "door-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        mvc.perform(post("/api/v1/organizer/staff").header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + address + "\"}"));
        return users.attendeeWithEmail(address);
    }

    @Test
    void countsByGateAndScanner() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        TestUser org = e.organizer();
        TestUser door = staff(org);
        UUID one = gate(org, e.eventId(), "Gate 1");
        UUID two = gate(org, e.eventId(), "Gate 2");
        BookingResponse b = support.confirmed(e, 4, users.attendee());
        scan(org, e.eventId(), one, b.tickets().get(0).qrToken());
        scan(org, e.eventId(), one, b.tickets().get(1).qrToken());
        scan(door, e.eventId(), two, b.tickets().get(2).qrToken());
        scan(door, e.eventId(), two, "garbage");

        for (TestUser viewer : new TestUser[] {org, door}) {
            mvc.perform(get("/api/v1/checkin/events/{id}/stats", e.eventId()).header("Authorization", viewer.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkedIn").value(3))
                    .andExpect(jsonPath("$.total").value(4))
                    .andExpect(jsonPath("$.byGate[0].name").value("Gate 1"))
                    .andExpect(jsonPath("$.byGate[0].checkedIn").value(2))
                    .andExpect(jsonPath("$.byGate[1].checkedIn").value(1))
                    .andExpect(jsonPath("$.byScanner[0].checkedIn").value(2))
                    .andExpect(jsonPath("$.byScanner[1].name").value("Door Person"))
                    .andExpect(jsonPath("$.lastScanAt").exists());
        }
        mvc.perform(get("/api/v1/checkin/events/{id}/stats", e.eventId()).header("Authorization", users.attendee().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void scanLogNewestFirstAndFilterable() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        TestUser org = e.organizer();
        TestUser door = staff(org);
        UUID one = gate(org, e.eventId(), "Gate 1");
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        scan(org, e.eventId(), one, b.tickets().get(0).qrToken());
        scan(org, e.eventId(), one, "not-a-ticket");

        mvc.perform(get("/api/v1/organizer/events/{id}/scan-log", e.eventId()).header("Authorization", org.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].result").value("INVALID"))
                .andExpect(jsonPath("$.items[1].result").value("VALID"))
                .andExpect(jsonPath("$.items[1].ticketCode").value(b.tickets().get(0).code()))
                .andExpect(jsonPath("$.items[1].gateName").value("Gate 1"));
        mvc.perform(get("/api/v1/organizer/events/{id}/scan-log?result=INVALID", e.eventId()).header("Authorization", org.bearer()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/organizer/events/{id}/scan-log", e.eventId()).header("Authorization", door.bearer()))
                .andExpect(status().isForbidden());
    }
}
