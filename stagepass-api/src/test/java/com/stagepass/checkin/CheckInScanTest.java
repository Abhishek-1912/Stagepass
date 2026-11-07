package com.stagepass.checkin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import com.stagepass.ticketing.service.QrTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInScanTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions scan(TestUser user, UUID eventId, String field, String value) throws Exception {
        String body = "{\"eventId\":\"%s\",\"%s\":%s}".formatted(eventId, field,
                value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        return mvc.perform(post("/api/v1/checkin/scan").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private record Sold(LiveEvent event, BookingResponse booking) {
        String token(int i) {
            return booking.tickets().get(i).qrToken();
        }
    }

    private Sold sold(int qty) {
        LiveEvent e = support.liveEvent(49900, 20);
        return new Sold(e, support.confirmed(e, qty, users.attendee()));
    }

    private String lastLog(UUID eventId) {
        return jdbc.queryForObject("select result || '|' || manual from scan_logs where event_id = ? order by at desc limit 1",
                String.class, eventId);
    }

    @Test
    void validTicketChecksInOnce() throws Exception {
        Sold s = sold(2);

        scan(s.event().organizer(), s.event().eventId(), "token", s.token(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("VALID"))
                .andExpect(jsonPath("$.ticket.holderName").value("Asha Rao"))
                .andExpect(jsonPath("$.ticket.code").value(s.booking().tickets().get(0).code()))
                .andExpect(jsonPath("$.ticket.ticketType").exists())
                .andExpect(jsonPath("$.counts.checkedIn").value(1))
                .andExpect(jsonPath("$.counts.total").value(2));

        assertThat(jdbc.queryForObject("select status from tickets where id = ?", String.class,
                s.booking().tickets().get(0).id())).isEqualTo("CHECKED_IN");
        assertThat(lastLog(s.event().eventId())).isEqualTo("VALID|false");
    }

    @Test
    void secondScanIsAlreadyUsed() throws Exception {
        Sold s = sold(1);
        scan(s.event().organizer(), s.event().eventId(), "token", s.token(0)).andExpect(jsonPath("$.result").value("VALID"));

        scan(s.event().organizer(), s.event().eventId(), "token", s.token(0))
                .andExpect(jsonPath("$.result").value("ALREADY_USED"))
                .andExpect(jsonPath("$.firstScan.byYou").value(true))
                .andExpect(jsonPath("$.firstScan.at").exists())
                .andExpect(jsonPath("$.counts.checkedIn").value(1));
        assertThat(jdbc.queryForObject("select count(*) from check_ins where event_id = ?", Integer.class,
                s.event().eventId())).isEqualTo(1);
    }

    @Test
    void manualCodeWorksCaseInsensitively() throws Exception {
        Sold s = sold(1);
        String code = " " + s.booking().tickets().get(0).code().toLowerCase() + " ";

        scan(s.event().organizer(), s.event().eventId(), "code", code).andExpect(jsonPath("$.result").value("VALID"));
        assertThat(lastLog(s.event().eventId())).isEqualTo("VALID|true");
    }

    @Test
    void refundedTicketIsInvalid() throws Exception {
        Sold s = sold(2);
        jdbc.update("update tickets set status = 'REFUNDED' where id = ?", s.booking().tickets().get(0).id());

        scan(s.event().organizer(), s.event().eventId(), "token", s.token(0))
                .andExpect(jsonPath("$.result").value("INVALID"))
                .andExpect(jsonPath("$.message").value("This ticket was refunded"));
        assertThat(jdbc.queryForObject("select count(*) from check_ins where event_id = ?", Integer.class,
                s.event().eventId())).isZero();
    }

    @Test
    void forgedRandomAndRotatedTokensAreInvalid() throws Exception {
        Sold s = sold(2);
        TestUser org = s.event().organizer();
        UUID event = s.event().eventId();
        String real = s.token(0);
        String edited = real.substring(0, 5) + (real.charAt(5) == 'A' ? 'B' : 'A') + real.substring(6);
        String foreign = new QrTokens("another-secret-another-secret-9999")
                .sign(s.booking().tickets().get(1).id(), event, "x");

        for (String bad : new String[] {edited, foreign, "upi://pay?pa=shop@okaxis&am=10", "", "x".repeat(10_000)}) {
            scan(org, event, "token", bad).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("INVALID"));
        }
        jdbc.update("update tickets set qr_nonce = 'rotated' where id = ?", s.booking().tickets().get(1).id());
        scan(org, event, "token", s.token(1)).andExpect(jsonPath("$.result").value("INVALID"))
                .andExpect(jsonPath("$.message").value("Not a valid Stagepass ticket"));
        scan(org, event, "code", "SP-ZZZZ-ZZZZ").andExpect(jsonPath("$.result").value("INVALID"));
    }

    @Test
    void ticketForAnotherEventIsWrongEvent() throws Exception {
        Sold s = sold(1);
        TestUser org = s.event().organizer();
        LiveEvent other = support.liveEvent(org, 49900, 20);
        jdbc.update("update events set title = 'The Other Show' where id = ?", s.event().eventId());

        scan(org, other.eventId(), "token", s.token(0))
                .andExpect(jsonPath("$.result").value("WRONG_EVENT"))
                .andExpect(jsonPath("$.message").value(containsString("The Other Show")));
        assertThat(lastLog(other.eventId())).isEqualTo("WRONG_EVENT|false");
    }

    @Test
    void needsExactlyOneOfTokenAndCode() throws Exception {
        Sold s = sold(1);
        mvc.perform(post("/api/v1/checkin/scan").header("Authorization", s.event().organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"" + s.event().eventId() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/checkin/scan").header("Authorization", s.event().organizer().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + s.event().eventId() + "\",\"token\":\"a\",\"code\":\"b\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void strangerGets404() throws Exception {
        Sold s = sold(1);
        scan(users.approvedOrganizer(), s.event().eventId(), "token", s.token(0)).andExpect(status().isNotFound());
        scan(users.attendee(), s.event().eventId(), "token", s.token(0)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from check_ins where event_id = ?", Integer.class,
                s.event().eventId())).isZero();
    }

    @Test
    void closedEventRefusesScans() throws Exception {
        Sold s = sold(1);
        jdbc.update("update events set status = 'COMPLETED', starts_at = now() - interval '3 days', "
                + "ends_at = now() - interval '2 days' where id = ?", s.event().eventId());
        scan(s.event().organizer(), s.event().eventId(), "token", s.token(0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
