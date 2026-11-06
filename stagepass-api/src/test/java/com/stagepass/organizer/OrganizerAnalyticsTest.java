package com.stagepass.organizer;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.BookingExpiryService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizerAnalyticsTest extends PostgresIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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

    private ResultActions getAs(TestUser user, String path, Object... vars) throws Exception {
        return mvc.perform(get(path, vars).header("Authorization", user.bearer()));
    }

    @Test
    void overviewCountsOnlyLiveTickets() throws Exception {
        TestUser org = users.approvedOrganizer();
        LiveEvent e = support.liveEvent(org, 49900, 50);
        jdbc.update("update events set refund_policy = 'UNTIL_HOURS_BEFORE', refund_cutoff_hours = 48 where id = ?", e.eventId());
        TestUser buyer = users.attendee();
        BookingResponse two = support.confirmed(e, 2, buyer);
        support.confirmed(e, 1, users.attendee());
        support.book(e, 1, users.attendee());
        BookingResponse abandoned = support.book(e, 1, users.attendee());
        new TransactionTemplate(txManager).executeWithoutResult(s -> expiry.expire(abandoned.id(), "HOLD_EXPIRED"));
        mvc.perform(post("/api/v1/me/bookings/{id}/refunds", two.id()).header("Authorization", buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketIds\":[\"" + two.tickets().get(0).id() + "\"]}"))
                .andExpect(status().isOk());

        getAs(org, "/api/v1/organizer/overview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveEvents").value(1))
                .andExpect(jsonPath("$.upcomingEvents").value(1))
                .andExpect(jsonPath("$.ticketsSold").value(2))
                .andExpect(jsonPath("$.grossSalesPaise").value(149700))
                .andExpect(jsonPath("$.netSalesPaise").value(99800))
                .andExpect(jsonPath("$.earningsPaise").value(99800))
                .andExpect(jsonPath("$.recentBookings.length()").value(2))
                .andExpect(jsonPath("$.dailySales.length()").value(30));
    }

    @Test
    void eventStatsByType() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        BookingResponse b = support.confirmed(e, 3, users.attendee());
        support.book(e, 2, users.attendee());
        jdbc.update("update tickets set status = 'CHECKED_IN' where id = ?", b.tickets().get(0).id());

        String json = getAs(e.organizer(), "/api/v1/organizer/events/{id}/stats?days=7", e.eventId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(150)) // fixture's default type (100) + this type (50)
                .andExpect(jsonPath("$.ticketsSold").value(3))
                .andExpect(jsonPath("$.held").value(2))
                .andExpect(jsonPath("$.checkedIn").value(1))
                .andExpect(jsonPath("$.grossSalesPaise").value(149700))
                .andExpect(jsonPath("$.netSalesPaise").value(149700))
                .andExpect(jsonPath("$.dailySales.length()").value(7))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> types = JsonPath.read(json, "$.byType[?(@.ticketTypeId == '" + e.ticketTypeId() + "')]");
        assertThat(types).singleElement().satisfies(t -> {
            assertThat(t).containsEntry("total", 50).containsEntry("sold", 3).containsEntry("held", 2)
                    .containsEntry("pricePaise", 49900).containsEntry("netSalesPaise", 149700);
        });
    }

    @Test
    void dailySalesUseIstDates() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        BookingResponse late = support.confirmed(e, 1, users.attendee());
        BookingResponse next = support.confirmed(e, 2, users.attendee());
        LocalDate day = LocalDate.now(IST).minusDays(2);
        // 23:30 IST on `day` is 18:00 UTC the same day; 00:30 IST the next day is still `day` in UTC.
        jdbc.update("update bookings set confirmed_at = ? where id = ?",
                day.atTime(23, 30).atZone(IST).toOffsetDateTime(), late.id());
        jdbc.update("update bookings set confirmed_at = ? where id = ?",
                day.plusDays(1).atTime(0, 30).atZone(IST).toOffsetDateTime(), next.id());

        String json = getAs(e.organizer(), "/api/v1/organizer/events/{id}/stats?days=7", e.eventId())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> dates = JsonPath.read(json, "$.dailySales[*].date");
        assertThat(dates).hasSize(7).last().isEqualTo(LocalDate.now(IST).toString());
        assertThat(JsonPath.<List<Integer>>read(json, "$.dailySales[?(@.date == '" + day + "')].tickets")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(json, "$.dailySales[?(@.date == '" + day.plusDays(1) + "')].tickets"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(json, "$.dailySales[?(@.date == '" + day.minusDays(1) + "')].tickets"))
                .containsExactly(0);
        assertThat(OffsetDateTime.now(ZoneOffset.UTC)).isNotNull();
    }

    @Test
    void otherOrganizersEventIs404() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        getAs(users.approvedOrganizer(), "/api/v1/organizer/events/{id}/stats", e.eventId()).andExpect(status().isNotFound());
    }

    @Test
    void unknownDaysValueIs400() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        getAs(e.organizer(), "/api/v1/organizer/overview?days=12").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("days"));
    }

    @Test
    void notApprovedOrganizerIs403() throws Exception {
        getAs(users.pendingOrganizer(), "/api/v1/organizer/overview").andExpect(status().isForbidden());
        getAs(users.attendee(), "/api/v1/organizer/overview").andExpect(status().isForbidden());
    }
}
