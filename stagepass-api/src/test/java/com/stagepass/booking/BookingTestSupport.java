package com.stagepass.booking;

import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.TestUsers;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Builds request bodies and live events for booking tests. */
@Component
public class BookingTestSupport {

    private final CatalogFixtures catalog;
    private final TestUsers users;

    public BookingTestSupport(CatalogFixtures catalog, TestUsers users) {
        this.catalog = catalog;
        this.users = users;
    }

    public record LiveEvent(UUID eventId, UUID ticketTypeId, TestUsers.TestUser organizer) {
    }

    /** A published event 10 days out with one extra ACTIVE type at {@code pricePaise} × {@code quantity}. */
    public LiveEvent liveEvent(long pricePaise, int quantity) {
        TestUsers.TestUser org = users.approvedOrganizer();
        UUID event = catalog.publishedEvent(org, "Booking test", OffsetDateTime.now().plusDays(10), "Bookcity", 100);
        UUID type = catalog.ticketType(event, pricePaise, quantity);
        return new LiveEvent(event, type, org);
    }

    public static String items(UUID eventId, UUID typeId, int qty) {
        return "{\"eventId\":\"%s\",\"items\":[{\"ticketTypeId\":\"%s\",\"quantity\":%d}]}".formatted(eventId, typeId, qty);
    }

    public static String booking(UUID eventId, UUID typeId, int qty) {
        return """
                {"eventId":"%s","items":[{"ticketTypeId":"%s","quantity":%d}],
                 "attendeeName":"Asha Rao","attendeeEmail":"asha@test.in","attendeePhone":"9876543210","buyerState":"Telangana"}
                """.formatted(eventId, typeId, qty);
    }
}
