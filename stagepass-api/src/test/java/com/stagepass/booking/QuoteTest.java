package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuoteTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions quote(String body) throws Exception {
        return mvc.perform(post("/api/v1/public/quote").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void quotesTwoTicketsWithFees() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        quote(BookingTestSupport.items(e.eventId(), e.ticketTypeId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.subtotalPaise").value(99800))
                .andExpect(jsonPath("$.price.feePaise").value(4495))
                .andExpect(jsonPath("$.price.feeGstPaise").value(809))
                .andExpect(jsonPath("$.price.totalPaise").value(105104))
                .andExpect(jsonPath("$.lines[0].amountPaise").value(99800))
                .andExpect(jsonPath("$.holdMinutes").value(10));
    }

    @Test
    void rejectsHiddenEndedAndOutOfRangeQuantities() throws Exception {
        LiveEvent e = support.liveEvent(49900, 50);
        UUID hidden = catalog.ticketType(e.eventId(), 100, 10);
        jdbc.update("update ticket_types set status = 'HIDDEN' where id = ?", hidden);
        UUID ended = catalog.ticketType(e.eventId(), 100, 10);
        jdbc.update("update ticket_types set sale_ends_at = now() - interval '1 minute' where id = ?", ended);
        UUID pairs = catalog.ticketType(e.eventId(), 100, 10);
        jdbc.update("update ticket_types set min_per_order = 2, max_per_order = 4 where id = ?", pairs);

        quote(BookingTestSupport.items(e.eventId(), hidden, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("items[0]")));
        quote(BookingTestSupport.items(e.eventId(), ended, 1)).andExpect(status().isBadRequest());
        quote(BookingTestSupport.items(e.eventId(), pairs, 1)).andExpect(status().isBadRequest());
        quote(BookingTestSupport.items(e.eventId(), pairs, 5)).andExpect(status().isBadRequest());
        quote(BookingTestSupport.items(e.eventId(), pairs, 2)).andExpect(status().isOk());
    }

    @Test
    void notEnoughTicketsIs409SoldOut() throws Exception {
        LiveEvent e = support.liveEvent(100, 3);
        quote(BookingTestSupport.items(e.eventId(), e.ticketTypeId(), 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    @Test
    void typeFromAnotherEventIsRejected() throws Exception {
        LiveEvent a = support.liveEvent(100, 3);
        LiveEvent b = support.liveEvent(100, 3);
        quote(BookingTestSupport.items(a.eventId(), b.ticketTypeId(), 1)).andExpect(status().isBadRequest());
    }

    @Test
    void unpublishedEventCannotBeQuoted() throws Exception {
        LiveEvent e = support.liveEvent(100, 3);
        catalog.setStatus(e.eventId(), "DRAFT");
        quote(BookingTestSupport.items(e.eventId(), e.ticketTypeId(), 1)).andExpect(status().isNotFound());
    }
}
