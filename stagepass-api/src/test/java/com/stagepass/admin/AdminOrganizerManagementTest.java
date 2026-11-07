package com.stagepass.admin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOrganizerManagementTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions suspend(TestUser admin, UUID organizerId) throws Exception {
        return mvc.perform(post("/api/v1/admin/organizers/" + organizerId + "/suspend").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fake events reported\"}"));
    }

    private ResultActions update(TestUser admin, UUID organizerId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/admin/organizers/" + organizerId).header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions scan(TestUser user, UUID eventId, String token) throws Exception {
        return mvc.perform(post("/api/v1/checkin/scan").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"%s\",\"token\":\"%s\"}".formatted(eventId, token)));
    }

    private String slug(UUID eventId) {
        return jdbc.queryForObject("select slug from events where id = ?", String.class, eventId);
    }

    @Test
    void listFiltersAndShowsPayable() throws Exception {
        TestUser admin = users.admin();
        TestUser org = users.approvedOrganizer();
        UUID finished = catalog.publishedEvent(org, "Done gig", OffsetDateTime.now().minusDays(5), "Paycity", 100);
        jdbc.update("insert into organizer_ledger_entries (organizer_id, event_id, type, amount_paise) values (?, ?, 'SALE', 99800)",
                org.organizerId(), finished);
        String contact = jdbc.queryForObject("select contact_email from organizers where id = ?", String.class, org.organizerId());

        mvc.perform(get("/api/v1/admin/organizers").param("q", contact.toUpperCase()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(org.organizerId().toString()))
                .andExpect(jsonPath("$.items[0].payablePaise").value(99800))
                .andExpect(jsonPath("$.items[0].negative").value(false))
                .andExpect(jsonPath("$.items[0].eventsCount").value(1));
        suspend(admin, org.organizerId()).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/organizers").param("status", "SUSPENDED").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].id", hasItem(org.organizerId().toString())));
        mvc.perform(get("/api/v1/admin/organizers").param("status", "APPROVED").param("size", "100")
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(org.organizerId().toString()))));
        mvc.perform(get("/api/v1/admin/organizers/" + org.organizerId()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pan").value("ABCDE1234F"))
                .andExpect(jsonPath("$.balances.payablePaise").value(99800))
                .andExpect(jsonPath("$.payouts").isArray());
        mvc.perform(get("/api/v1/admin/organizers").param("status", "nope").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendHidesEventsBlocksDashboardKeepsDoor() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 2, users.attendee());
        String door = "door-" + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        mvc.perform(post("/api/v1/organizer/staff").header("Authorization", e.organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"%s\"}".formatted(door)))
                .andExpect(status().isCreated());
        TestUser staff = users.attendeeWithEmail(door);

        suspend(admin, e.organizer().organizerId()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings").header("Authorization", users.attendee().bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BookingTestSupport.booking(e.eventId(), e.ticketTypeId(), 1)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organizer/overview").header("Authorization", e.organizer().bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORGANIZER_NOT_APPROVED"));
        scan(e.organizer(), e.eventId(), b.tickets().get(0).qrToken()).andExpect(jsonPath("$.result").value("VALID"));
        scan(staff, e.eventId(), b.tickets().get(1).qrToken()).andExpect(jsonPath("$.result").value("VALID"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ORGANIZER_SUSPENDED' and entity_id = ?",
                Integer.class, e.organizer().organizerId().toString())).isEqualTo(1);
    }

    @Test
    void reinstateRestores() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(49900, 20);
        suspend(admin, e.organizer().organizerId()).andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/organizers/" + e.organizer().organizerId() + "/reinstate")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/public/events/" + slug(e.eventId()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizer/overview").header("Authorization", e.organizer().bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/organizers/" + e.organizer().organizerId() + "/reinstate")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void suspendOnlyFromApproved() throws Exception {
        suspend(users.admin(), users.pendingOrganizer().organizerId()).andExpect(status().isConflict());
        suspend(users.admin(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void updateValidatesAndAuditsChangedFieldsOnly() throws Exception {
        TestUser admin = users.admin();
        TestUser org = users.approvedOrganizer();
        jdbc.update("update organizers set gstin = '36ABCDE1234F1Z5' where id = ?", org.organizerId());

        update(admin, org.organizerId(), "{\"pan\":\"abcde12345\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("pan"));
        update(admin, org.organizerId(), "{\"gstin\":\"XYZ\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("gstin"));
        update(admin, org.organizerId(), "{\"commissionBpsOverride\":6000}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("commissionBpsOverride"));
        update(admin, org.organizerId(), "{\"orgName\":\" \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("orgName"));

        update(admin, org.organizerId(), "{\"pan\":\"pqrst6789k\",\"gstin\":\"\",\"orgName\":\"Test Org\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pan").value("PQRST6789K"))
                .andExpect(jsonPath("$.gstin").doesNotExist());
        Map<String, Object> audit = jdbc.queryForMap("""
                select before::text as b, after::text as a from audit_logs
                where action = 'ORGANIZER_UPDATED' and entity_id = ?
                """, org.organizerId().toString());
        assertThat((String) audit.get("a")).contains("PQRST6789K").contains("gstin").doesNotContain("orgName");
        assertThat((String) audit.get("b")).contains("ABCDE1234F").contains("36ABCDE1234F1Z5");
    }

    @Test
    void commissionOverrideAffectsNextBooking() throws Exception {
        TestUser admin = users.admin();
        LiveEvent e = support.liveEvent(100000, 20);
        update(admin, e.organizer().organizerId(), "{\"commissionBpsOverride\":500}").andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionBpsOverride").value(500));

        BookingResponse b = support.confirmed(e, 1, users.attendee());
        assertThat(jdbc.queryForObject("select commission_paise from bookings where id = ?", Long.class, b.id())).isEqualTo(5000L);

        update(admin, e.organizer().organizerId(), "{\"commissionBpsOverride\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionBpsOverride").doesNotExist());
        BookingResponse after = support.confirmed(e, 1, users.attendee());
        assertThat(jdbc.queryForObject("select commission_paise from bookings where id = ?", Long.class, after.id())).isZero();
    }

    @Test
    void nonAdminGets403() throws Exception {
        TestUser org = users.approvedOrganizer();
        mvc.perform(get("/api/v1/admin/organizers").header("Authorization", org.bearer())).andExpect(status().isForbidden());
        suspend(org, org.organizerId()).andExpect(status().isForbidden());
        update(org, org.organizerId(), "{\"orgName\":\"Mine\"}").andExpect(status().isForbidden());
    }
}
