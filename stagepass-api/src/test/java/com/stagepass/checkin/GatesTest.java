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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatesTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions createGate(TestUser org, UUID eventId, String json) throws Exception {
        return mvc.perform(post("/api/v1/organizer/events/{id}/gates", eventId).header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID gate(TestUser org, UUID eventId, String name, UUID... types) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (UUID t : types) {
            ids.append(ids.isEmpty() ? "" : ",").append('"').append(t).append('"');
        }
        String json = createGate(org, eventId, "{\"name\":\"%s\",\"ticketTypeIds\":[%s]}".formatted(name, ids))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions scan(TestUser user, UUID eventId, UUID gateId, String token) throws Exception {
        return mvc.perform(post("/api/v1/checkin/scan").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"%s\",\"gateId\":\"%s\",\"token\":\"%s\"}".formatted(eventId, gateId, token)));
    }

    /** The fixture's default ticket type (not the one the test bookings use). */
    private UUID otherType(LiveEvent e) {
        return jdbc.queryForObject("select id from ticket_types where event_id = ? and id <> ? limit 1", UUID.class,
                e.eventId(), e.ticketTypeId());
    }

    @Test
    void gateLimitedToTypesSaysWrongGate() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        TestUser org = e.organizer();
        UUID vip = gate(org, e.eventId(), "VIP Gate", otherType(e));
        UUID main = gate(org, e.eventId(), "Main Gate");
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        String token = b.tickets().get(0).qrToken();

        scan(org, e.eventId(), vip, token)
                .andExpect(jsonPath("$.result").value("WRONG_GATE"))
                .andExpect(jsonPath("$.allowedGates", contains("Main Gate")))
                .andExpect(jsonPath("$.message").value("Wrong gate. Use Main Gate"));
        assertThat(jdbc.queryForObject("select count(*) from check_ins where event_id = ?", Integer.class, e.eventId())).isZero();

        scan(org, e.eventId(), main, token).andExpect(jsonPath("$.result").value("VALID"));
    }

    @Test
    void alreadyUsedNamesTheFirstGate() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        UUID one = gate(e.organizer(), e.eventId(), "Gate 1");
        UUID two = gate(e.organizer(), e.eventId(), "Gate 2");
        String token = support.confirmed(e, 1, users.attendee()).tickets().get(0).qrToken();

        scan(e.organizer(), e.eventId(), one, token).andExpect(jsonPath("$.result").value("VALID"));
        scan(e.organizer(), e.eventId(), two, token).andExpect(jsonPath("$.result").value("ALREADY_USED"))
                .andExpect(jsonPath("$.firstScan.gateName").value("Gate 1"));
    }

    @Test
    void gateFromAnotherEventIsRejected() throws Exception {
        TestUser org = users.approvedOrganizer();
        LiveEvent a = support.liveEvent(org, 49900, 20);
        LiveEvent b = support.liveEvent(org, 49900, 20);
        UUID gateOfB = gate(org, b.eventId(), "Gate B");
        String token = support.confirmed(a, 1, users.attendee()).tickets().get(0).qrToken();

        scan(org, a.eventId(), gateOfB, token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("gateId"));
    }

    @Test
    void gateCrudValidates() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        TestUser org = e.organizer();
        UUID gate = gate(org, e.eventId(), "Gate 1");
        createGate(org, e.eventId(), "{\"name\":\"gate 1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
        LiveEvent other = support.liveEvent(49900, 20);
        createGate(org, e.eventId(), "{\"name\":\"X\",\"ticketTypeIds\":[\"" + other.ticketTypeId() + "\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("ticketTypeIds")));

        mvc.perform(patch("/api/v1/organizer/events/{id}/gates/{gate}", e.eventId(), gate).header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"North Gate\",\"ticketTypeIds\":[\"" + e.ticketTypeId() + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("North Gate"))
                .andExpect(jsonPath("$.ticketTypeIds[0]").value(e.ticketTypeId().toString()));

        String token = support.confirmed(e, 1, users.attendee()).tickets().get(0).qrToken();
        scan(org, e.eventId(), gate, token).andExpect(jsonPath("$.result").value("VALID"));
        mvc.perform(get("/api/v1/organizer/events/{id}/gates", e.eventId()).header("Authorization", org.bearer()))
                .andExpect(jsonPath("$[0].checkedIn").value(1));

        mvc.perform(delete("/api/v1/organizer/events/{id}/gates/{gate}", e.eventId(), gate).header("Authorization", org.bearer()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from check_ins where event_id = ? and gate_id is null", Integer.class,
                e.eventId())).isEqualTo(1);
        mvc.perform(get("/api/v1/organizer/events/{id}/gates", other.eventId()).header("Authorization", org.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCannotEditGatesButSeeThemInTheScanner() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        gate(e.organizer(), e.eventId(), "VIP Gate", e.ticketTypeId());
        String address = "door-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        mvc.perform(post("/api/v1/organizer/staff").header("Authorization", e.organizer().bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + address + "\"}"));
        TestUser door = users.attendeeWithEmail(address);

        createGate(door, e.eventId(), "{\"name\":\"Mine\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/checkin/events").header("Authorization", door.bearer()))
                .andExpect(jsonPath("$[0].gates[0].name").value("VIP Gate"))
                .andExpect(jsonPath("$[0].gates[0].ticketTypeNames[0]").exists());
    }
}
