package com.stagepass.ticketing;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketDocumentControllerTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ownerDownloadsTicketPdf() throws Exception {
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 10), 2, buyer);

        byte[] body = mvc.perform(get("/api/v1/me/bookings/{id}/tickets.pdf", b.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"stagepass-" + b.code() + ".pdf\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void otherUserGets404() throws Exception {
        BookingResponse b = support.confirmed(support.liveEvent(49900, 10), 1, users.attendee());
        mvc.perform(get("/api/v1/me/bookings/{id}/tickets.pdf", b.id()).header("Authorization", users.attendee().bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/bookings/{id}/invoice.pdf", b.id()).header("Authorization", users.attendee().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullyRefundedBookingIs409() throws Exception {
        TestUser buyer = users.attendee();
        BookingResponse b = support.confirmed(support.liveEvent(49900, 10), 1, buyer);
        jdbc.update("update tickets set status = 'REFUNDED' where booking_id = ?", b.id());
        mvc.perform(get("/api/v1/me/bookings/{id}/tickets.pdf", b.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void invoiceDownloadAnd404WithoutInvoice() throws Exception {
        TestUser buyer = users.attendee();
        BookingResponse paid = support.confirmed(support.liveEvent(49900, 10), 1, buyer);
        mvc.perform(get("/api/v1/me/bookings/{id}/invoice.pdf", paid.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"stagepass-invoice-SP-")));

        BookingResponse free = support.confirmed(support.liveEvent(0, 10), 1, buyer);
        mvc.perform(get("/api/v1/me/bookings/{id}/invoice.pdf", free.id()).header("Authorization", buyer.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresLogin() throws Exception {
        BookingResponse b = support.confirmed(support.liveEvent(0, 10), 1, users.attendee());
        mvc.perform(get("/api/v1/me/bookings/{id}/tickets.pdf", b.id())).andExpect(status().isUnauthorized());
    }
}
