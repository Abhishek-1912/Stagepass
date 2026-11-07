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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffAccessTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions invite(TestUser org, String email, UUID eventId) throws Exception {
        String body = eventId == null ? "{\"email\":\"%s\"}".formatted(email)
                : "{\"email\":\"%s\",\"eventId\":\"%s\"}".formatted(email, eventId);
        return mvc.perform(post("/api/v1/organizer/staff").header("Authorization", org.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions scan(TestUser user, UUID eventId, String token) throws Exception {
        return mvc.perform(post("/api/v1/checkin/scan").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"%s\",\"token\":\"%s\"}".formatted(eventId, token)));
    }

    private static String email(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
    }

    @Test
    void invitedEmailCanScanAfterSigningIn() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        String address = email("door1");
        String staffId = JsonPath.read(invite(e.organizer(), address.toUpperCase(), e.eventId())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        TestUser door = users.attendeeWithEmail(address);
        mvc.perform(get("/api/v1/checkin/events").header("Authorization", door.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(e.eventId().toString())))
                .andExpect(jsonPath("$[0].owner").value(false));
        scan(door, e.eventId(), b.tickets().get(0).qrToken()).andExpect(jsonPath("$.result").value("VALID"));

        assertThat(jdbc.queryForMap("select user_id, accepted_at from event_staff where id = ?::uuid", staffId))
                .containsEntry("user_id", door.id()).extractingByKey("accepted_at").isNotNull();
        mvc.perform(get("/api/v1/organizer/staff").header("Authorization", e.organizer().bearer()))
                .andExpect(jsonPath("$[0].name").value("Door Person"))
                .andExpect(jsonPath("$[0].acceptedAt").exists());
    }

    @Test
    void allEventsInviteCoversEveryEvent() throws Exception {
        TestUser org = users.approvedOrganizer();
        LiveEvent one = support.liveEvent(org, 49900, 20);
        LiveEvent two = support.liveEvent(org, 49900, 20);
        String address = email("door2");
        invite(org, address, null).andExpect(status().isCreated()).andExpect(jsonPath("$.eventTitle").doesNotExist());
        TestUser door = users.attendeeWithEmail(address);

        String json = mvc.perform(get("/api/v1/checkin/events").header("Authorization", door.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[*].id");
        assertThat(ids).contains(one.eventId().toString(), two.eventId().toString());
        BookingResponse b = support.confirmed(two, 1, users.attendee());
        scan(door, two.eventId(), b.tickets().get(0).qrToken()).andExpect(jsonPath("$.result").value("VALID"));
    }

    @Test
    void removedStaffIsRefusedOnNextScan() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 2, users.attendee());
        String address = email("door3");
        TestUser door = users.attendeeWithEmail(address);
        String staffId = JsonPath.read(invite(e.organizer(), address, e.eventId()).andReturn().getResponse().getContentAsString(),
                "$.id");
        scan(door, e.eventId(), b.tickets().get(0).qrToken()).andExpect(jsonPath("$.result").value("VALID"));

        mvc.perform(delete("/api/v1/organizer/staff/{id}", staffId).header("Authorization", e.organizer().bearer()))
                .andExpect(status().isNoContent());

        scan(door, e.eventId(), b.tickets().get(1).qrToken()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from event_staff where id = ?::uuid", String.class, staffId))
                .isEqualTo("REMOVED");
    }

    @Test
    void staffCannotReachOtherOrganizersEvents() throws Exception {
        LiveEvent mine = support.liveEvent(49900, 20);
        LiveEvent theirs = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(theirs, 1, users.attendee());
        String address = email("door4");
        invite(mine.organizer(), address, null);
        TestUser door = users.attendeeWithEmail(address);

        mvc.perform(get("/api/v1/checkin/events").header("Authorization", door.bearer()))
                .andExpect(jsonPath("$[*].id", not(hasItem(theirs.eventId().toString()))));
        scan(door, theirs.eventId(), b.tickets().get(0).qrToken()).andExpect(status().isNotFound());
    }

    @Test
    void staffCannotSeeOrganizerData() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        String address = email("door5");
        invite(e.organizer(), address, e.eventId());
        TestUser door = users.attendeeWithEmail(address);

        mvc.perform(get("/api/v1/organizer/overview").header("Authorization", door.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizer/events/{id}/attendees", e.eventId()).header("Authorization", door.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteIsIdempotentAndEmailed() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        String address = email("door6");
        String first = JsonPath.read(invite(e.organizer(), address, e.eventId()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(invite(e.organizer(), address.toUpperCase(), e.eventId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where template = 'STAFF_INVITED' and dedupe_key = ?",
                Integer.class, "staff-invited:" + first)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_address from email_outbox where dedupe_key = ?", String.class,
                "staff-invited:" + first)).isEqualTo(address);
    }

    @Test
    void inviteForForeignEventIs404() throws Exception {
        LiveEvent theirs = support.liveEvent(49900, 20);
        invite(users.approvedOrganizer(), email("door7"), theirs.eventId()).andExpect(status().isNotFound());
    }
}
