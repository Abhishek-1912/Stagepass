package com.stagepass.booking;

import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.Invoice;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.repository.InvoiceRepository;
import com.stagepass.booking.service.InvoiceService;
import com.stagepass.common.config.CompanyProperties;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceServiceTest extends PostgresIntegrationTest {

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    BookingRepository bookings;

    @Autowired
    InvoiceRepository invoiceRepo;

    @Autowired
    InvoiceService invoices;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    /** A pending 2 × ₹499 booking (fee 4495, fee GST 809), marked confirmed at {@code at}, bought from {@code state}. */
    private Optional<Invoice> issue(InvoiceService service, String at, String state) {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse r = support.book(e, 2, users.attendee());
        return new TransactionTemplate(txManager).execute(s -> {
            Booking b = bookings.findById(r.id()).orElseThrow();
            b.setConfirmedAt(OffsetDateTime.parse(at));
            b.setBuyerState(state);
            return service.issueFor(b);
        });
    }

    @Test
    void sameStateSplitsCgstSgst() {
        Invoice inv = issue(invoices, "2041-06-01T10:00:00+05:30", "telangana").orElseThrow();
        assertThat(inv.getNumber()).isEqualTo("SP/2041-42/000001");
        assertThat(inv.getTaxablePaise()).isEqualTo(4495);
        assertThat(inv.getCgstPaise()).isEqualTo(405);
        assertThat(inv.getSgstPaise()).isEqualTo(404);
        assertThat(inv.getIgstPaise()).isZero();
        assertThat(inv.getTotalPaise()).isEqualTo(5304);

        Invoice next = issue(invoices, "2041-06-02T10:00:00+05:30", "Telangana").orElseThrow();
        assertThat(next.getNumber()).isEqualTo("SP/2041-42/000002");
    }

    @Test
    void otherStateUsesIgst() {
        Invoice inv = issue(invoices, "2042-06-01T10:00:00+05:30", "Karnataka").orElseThrow();
        assertThat(inv.getIgstPaise()).isEqualTo(809);
        assertThat(inv.getCgstPaise()).isZero();
        assertThat(inv.getSgstPaise()).isZero();
    }

    @Test
    void unknownBuyerStateUsesIgst() {
        Invoice inv = issue(invoices, "2042-07-01T10:00:00+05:30", null).orElseThrow();
        assertThat(inv.getIgstPaise()).isEqualTo(809);
    }

    @Test
    void noGstinNoInvoice() {
        InvoiceService unregistered = new InvoiceService(invoiceRepo, jdbc,
                new CompanyProperties("Stagepass", null, "Telangana", null));
        assertThat(issue(unregistered, "2043-06-01T10:00:00+05:30", "Telangana")).isEmpty();
    }

    @Test
    void financialYearRollsOverInApril() {
        assertThat(issue(invoices, "2045-03-31T23:00:00+05:30", "Telangana").orElseThrow().getNumber())
                .startsWith("SP/2044-45/");
        assertThat(issue(invoices, "2045-04-01T00:30:00+05:30", "Telangana").orElseThrow().getNumber())
                .startsWith("SP/2045-46/");
        // 31 March 19:00 UTC is already 1 April in India.
        assertThat(issue(invoices, "2046-03-31T19:00:00Z", "Telangana").orElseThrow().getNumber())
                .startsWith("SP/2046-47/");
    }

    @Test
    void issuingTwiceReturnsTheSameInvoice() {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse r = support.book(e, 2, users.attendee());
        TransactionTemplate tx = new TransactionTemplate(txManager);
        String first = tx.execute(s -> {
            Booking b = bookings.findById(r.id()).orElseThrow();
            b.setConfirmedAt(OffsetDateTime.parse("2047-06-01T10:00:00+05:30"));
            return invoices.issueFor(b).orElseThrow().getNumber();
        });
        String second = tx.execute(s -> invoices.issueFor(bookings.findById(r.id()).orElseThrow()).orElseThrow().getNumber());
        assertThat(second).isEqualTo(first);
    }
}
