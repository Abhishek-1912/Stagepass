package com.stagepass.checkin;

import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.checkin.api.ScanRequest;
import com.stagepass.checkin.api.ScanResponse;
import com.stagepass.checkin.service.CheckInService;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Final-review findings on Plan 7. */
class CheckInReviewFixesTest extends PostgresIntegrationTest {

    @Autowired
    CheckInService checkIn;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void scanWaitingOnARefundSeesTheRefund() throws Exception {
        LiveEvent e = support.liveEvent(49900, 20);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        var ticket = b.tickets().get(0);
        CountDownLatch refundHeld = new CountDownLatch(1);
        CountDownLatch releaseRefund = new CountDownLatch(1);

        // A refund that has updated the ticket but not committed yet.
        Future<?> refund = CompletableFuture.runAsync(() -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
            jdbc.update("update tickets set status = 'REFUNDED' where id = ?", ticket.id());
            refundHeld.countDown();
            try {
                releaseRefund.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        refundHeld.await(10, TimeUnit.SECONDS);

        Future<ScanResponse> scan = CompletableFuture.supplyAsync(
                () -> checkIn.scan(e.organizer().id(), new ScanRequest(e.eventId(), null, ticket.qrToken(), null)));
        Thread.sleep(500); // let the scan reach the ticket while the refund still holds it
        releaseRefund.countDown();
        refund.get(10, TimeUnit.SECONDS);

        assertThat(scan.get(10, TimeUnit.SECONDS).result()).isEqualTo("INVALID");
        assertThat(jdbc.queryForObject("select status from tickets where id = ?", String.class, ticket.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select count(*) from check_ins where ticket_id = ?", Integer.class, ticket.id()))
                .isZero();
    }

    @Test
    void scanLogHidesOtherEventsTickets() throws Exception {
        TestUser orgA = users.approvedOrganizer();
        LiveEvent a = support.liveEvent(orgA, 49900, 20);
        LiveEvent b = support.liveEvent(49900, 20);
        BookingResponse theirs = support.confirmed(b, 1, users.attendee());

        mvc.perform(post("/api/v1/checkin/scan").header("Authorization", orgA.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"%s\",\"token\":\"%s\"}".formatted(a.eventId(), theirs.tickets().get(0).qrToken())))
                .andExpect(jsonPath("$.result").value("WRONG_EVENT"));

        mvc.perform(get("/api/v1/organizer/events/{id}/scan-log", a.eventId()).header("Authorization", orgA.bearer()))
                .andExpect(jsonPath("$.items[0].result").value("WRONG_EVENT"))
                .andExpect(jsonPath("$.items[0].ticketCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].holderName").doesNotExist());
    }
}
