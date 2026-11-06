package com.stagepass.booking;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.BookingItem;
import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.LedgerEntry;
import com.stagepass.booking.domain.LedgerType;
import com.stagepass.booking.domain.Ticket;
import com.stagepass.booking.domain.TicketStatus;
import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.booking.repository.LedgerEntryRepository;
import com.stagepass.payment.domain.Payment;
import com.stagepass.payment.domain.PaymentStatus;
import com.stagepass.payment.repository.PaymentRepository;
import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class BookingMappingTest extends PostgresIntegrationTest {

    @Autowired
    BookingRepository bookings;

    @Autowired
    PaymentRepository payments;

    @Autowired
    LedgerEntryRepository ledger;

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    EntityManager em;

    private Booking pending(UUID userId, UUID eventId, String code) {
        Booking b = new Booking();
        b.setCode(code);
        b.setUserId(userId);
        b.setEventId(eventId);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setSubtotalPaise(99800);
        b.setFeePaise(4495);
        b.setFeeGstPaise(809);
        b.setTotalPaise(105104);
        b.setAttendeeName("Asha");
        b.setAttendeeEmail("asha@test.in");
        b.setHoldExpiresAt(OffsetDateTime.now().plusMinutes(10));
        return b;
    }

    @Test
    void persistsBookingWithItemsTicketsPaymentAndLedger() {
        TestUser org = users.approvedOrganizer();
        TestUser buyer = users.attendee();
        UUID event = catalog.publishedEvent(org, "Mapped", OffsetDateTime.now().plusDays(5), "Mapcity", 49900);
        UUID type = catalog.ticketType(event, 49900, 10);

        Booking b = pending(buyer.id(), event, "SP-MAP" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        BookingItem item = new BookingItem();
        item.setBooking(b);
        item.setTicketTypeId(type);
        item.setQuantity(2);
        item.setUnitPricePaise(49900);
        b.getItems().add(item);
        bookings.saveAndFlush(b);

        for (int i = 0; i < 2; i++) {
            Ticket t = new Ticket();
            t.setBooking(b);
            t.setBookingItemId(item.getId());
            t.setTicketTypeId(type);
            t.setEventId(event);
            t.setCode("SP-T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            t.setHolderName("Asha");
            t.setQrNonce("n" + i);
            b.getTickets().add(t);
        }
        Payment p = new Payment();
        p.setBookingId(b.getId());
        p.setRazorpayOrderId("order_" + UUID.randomUUID());
        p.setAmountPaise(105104);
        payments.save(p);
        LedgerEntry sale = new LedgerEntry();
        sale.setOrganizerId(org.organizerId());
        sale.setEventId(event);
        sale.setBookingId(b.getId());
        sale.setType(LedgerType.SALE);
        sale.setAmountPaise(99800);
        ledger.save(sale);
        em.flush();
        em.clear();

        Booking reloaded = bookings.findById(b.getId()).orElseThrow();
        assertThat(reloaded.getItems()).hasSize(1);
        assertThat(reloaded.getTickets()).hasSize(2).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.VALID));
        assertThat(payments.findByBookingId(b.getId())).get().extracting(Payment::getStatus).isEqualTo(PaymentStatus.CREATED);
        assertThat(ledger.findByBookingId(b.getId())).hasSize(1);
        assertThat(bookings.findByUserIdAndIdempotencyKey(buyer.id(), "none")).isEmpty();
    }

    @Test
    void onlyOnePendingBookingPerUserAndEvent() {
        TestUser buyer = users.attendee();
        UUID event = catalog.publishedEvent(users.approvedOrganizer(), "One pending", OffsetDateTime.now().plusDays(5),
                "Mapcity", 100);
        bookings.saveAndFlush(pending(buyer.id(), event, "SP-ONE" + UUID.randomUUID().toString().substring(0, 4)));

        assertThatThrownBy(() -> bookings.saveAndFlush(pending(buyer.id(), event,
                "SP-TWO" + UUID.randomUUID().toString().substring(0, 4))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
