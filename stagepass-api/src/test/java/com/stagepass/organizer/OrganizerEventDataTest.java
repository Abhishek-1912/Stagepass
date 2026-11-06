package com.stagepass.organizer;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.BookingExpiryService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizerEventDataTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    com.stagepass.organizer.service.OrganizerBookingQueries queries;

    private ResultActions getAs(TestUser user, String path, Object... vars) throws Exception {
        return mvc.perform(get(path, vars).header("Authorization", user.bearer()));
    }

    @Test
    void bookingsSearchAndFilter() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        BookingResponse a = support.confirmed(e, 2, users.attendee());
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        jdbc.update("update bookings set attendee_email = 'Priya@Example.in', status = 'PARTIALLY_REFUNDED' where id = ?", b.id());
        BookingResponse gone = support.book(e, 1, users.attendee());
        new TransactionTemplate(txManager).executeWithoutResult(s -> expiry.expire(gone.id(), "HOLD_EXPIRED"));

        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings", e.eventId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].code", containsInAnyOrder(a.code(), b.code())));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings?q=" + a.code().toLowerCase(), e.eventId())
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].ticketCount").value(2));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings?q=priya@example", e.eventId())
                .andExpect(jsonPath("$.items[0].code").value(b.code()));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings?status=PARTIALLY_REFUNDED", e.eventId())
                .andExpect(jsonPath("$.totalItems").value(1));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings?status=EXPIRED", e.eventId())
                .andExpect(jsonPath("$.items[0].code").value(gone.code()));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/bookings?q=100%25_", e.eventId())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void attendeesOneRowPerTicket() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        BookingResponse b = support.confirmed(e, 3, users.attendee());
        jdbc.update("update tickets set status = 'REFUNDED' where id = ?", b.tickets().get(2).id());

        getAs(e.organizer(), "/api/v1/organizer/events/{id}/attendees", e.eventId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].bookingCode").value(b.code()))
                .andExpect(jsonPath("$.items[0].holderName").value("Asha Rao"))
                .andExpect(jsonPath("$.items[0].attendeeEmail").value("asha@test.in"));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/attendees?status=REFUNDED", e.eventId())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].ticketCode").value(b.tickets().get(2).code()));
        getAs(e.organizer(), "/api/v1/organizer/events/{id}/attendees?q=" + b.tickets().get(0).code(), e.eventId())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void csvHasBomHeaderAndGuardedCells() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        BookingResponse b = support.confirmed(e, 3, users.attendee());
        jdbc.update("update tickets set holder_name = '=1+1' where id = ?", b.tickets().get(0).id());
        jdbc.update("update tickets set holder_name = 'Rao, \"Asha\" राव' where id = ?", b.tickets().get(1).id());
        String slug = jdbc.queryForObject("select slug from events where id = ?", String.class, e.eventId());
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.BASIC_ISO_DATE);

        byte[] body = getAs(e.organizer(), "/api/v1/organizer/events/{id}/attendees.csv", e.eventId())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"attendees-" + slug + "-" + today + ".csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Ticket code,Ticket type,Holder name,Booking code,Email,Phone,Status,"
                + "Booked at (IST),Checked in at (IST)\r\n");
        assertThat(csv.split("\r\n")).hasSize(4);
        assertThat(csv).contains(",'=1+1,").contains(",\"Rao, \"\"Asha\"\" राव\",").contains(b.code());
        assertThat(jdbc.queryForObject("select after->>'rows' from audit_logs where action = 'ATTENDEES_EXPORTED' "
                + "and entity_id = ?", String.class, e.eventId().toString())).isEqualTo("3");
    }

    @Test
    void otherOrganizersEventIs404() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        support.confirmed(e, 1, users.attendee());
        TestUser other = users.approvedOrganizer();
        for (String path : new String[] {"/bookings", "/attendees", "/attendees.csv"}) {
            getAs(other, "/api/v1/organizer/events/{id}" + path, e.eventId()).andExpect(status().isNotFound());
        }
    }

    @Test
    void exportsTwoThousandRows() throws Exception {
        LiveEvent e = support.liveEvent(0, 5000);
        UUID booking = jdbc.queryForObject("""
                insert into bookings (code, user_id, event_id, status, subtotal_paise, total_paise, attendee_name,
                                      attendee_email, confirmed_at)
                values (?, ?, ?, 'CONFIRMED', 0, 0, 'Bulk Buyer', 'bulk@test.in', now()) returning id
                """, UUID.class, "SP-" + UUID.randomUUID().toString().substring(0, 8), users.attendee().id(), e.eventId());
        UUID item = jdbc.queryForObject("""
                insert into booking_items (booking_id, ticket_type_id, quantity, unit_price_paise)
                values (?, ?, 2000, 0) returning id
                """, UUID.class, booking, e.ticketTypeId());
        jdbc.update("""
                insert into tickets (booking_id, booking_item_id, ticket_type_id, event_id, code, holder_name, qr_nonce)
                select ?, ?, ?, ?, 'SP-' || substr(md5(random()::text || g), 1, 4) || '-' || lpad(g::text, 6, '0'),
                       'Holder ' || g, 'n' || g
                from generate_series(1, 2000) g
                """, booking, item, e.ticketTypeId(), e.eventId());

        byte[] body = getAs(e.organizer(), "/api/v1/organizer/events/{id}/attendees.csv", e.eventId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8).split("\r\n")).hasSize(2001);
    }

    @Test
    void exportReadsThroughAStreamingCursor() {
        assertThat(queries.exportFetchSize()).isEqualTo(500);
    }

    @Test
    void auditSurvivesAnExportThatFailsMidway() {
        LiveEvent e = support.liveEvent(49900, 50);
        support.confirmed(e, 2, users.attendee());
        java.io.Writer broken = new java.io.Writer() {
            private int writes;

            @Override
            public void write(char[] cbuf, int off, int len) throws java.io.IOException {
                if (++writes > 1) {
                    throw new java.io.IOException("client went away");
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> queries.writeAttendeesCsv(e.organizer().organizerId(), e.organizer().id(), e.eventId(), broken))
                .hasMessageContaining("client went away");

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ATTENDEES_EXPORTED' and entity_id = ?",
                Integer.class, e.eventId().toString())).isEqualTo(1);
    }
}
