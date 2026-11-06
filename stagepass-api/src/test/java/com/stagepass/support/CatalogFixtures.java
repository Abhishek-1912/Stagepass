package com.stagepass.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** SQL-level catalog rows for tests that need an existing venue, event or ticket type quickly. */
@Component
public class CatalogFixtures {

    private final JdbcTemplate jdbc;

    public CatalogFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID musicCategoryId() {
        return jdbc.queryForObject("select id from event_categories where slug = 'music'", UUID.class);
    }

    public UUID venue(TestUsers.TestUser organizer) {
        return jdbc.queryForObject("""
                insert into venues (organizer_id, name, address_line, city, state, pincode)
                values (?, 'Test Arena', '1 MG Road', 'Bengaluru', 'Karnataka', '560001') returning id
                """, UUID.class, organizer.organizerId());
    }

    /** A DRAFT event 30 days out with a 4-hour duration. */
    public UUID draftEvent(TestUsers.TestUser organizer) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(30).withNano(0);
        return jdbc.queryForObject("""
                insert into events (organizer_id, category_id, slug, title, starts_at, ends_at, status)
                values (?, ?, ?, 'Fixture Night', ?, ?, 'DRAFT') returning id
                """, UUID.class, organizer.organizerId(), musicCategoryId(), "fixture-" + UUID.randomUUID(), start,
                start.plusHours(4));
    }

    public UUID ticketType(UUID eventId, long pricePaise, int quantity) {
        UUID id = jdbc.queryForObject("""
                insert into ticket_types (event_id, name, price_paise) values (?, 'General', ?) returning id
                """, UUID.class, eventId, pricePaise);
        jdbc.update("insert into ticket_inventory (ticket_type_id, total) values (?, ?)", id, quantity);
        return id;
    }

    public UUID categoryId(String slug) {
        return jdbc.queryForObject("select id from event_categories where slug = ?", UUID.class, slug);
    }

    /** A live (PUBLISHED) event with venue, cover and one ACTIVE ticket type of 100. */
    public UUID publishedEvent(TestUsers.TestUser organizer, String title, OffsetDateTime start, String city,
            long pricePaise) {
        return publishedEvent(organizer, title, start, city, pricePaise, 12.9716, 77.5946, "music");
    }

    public UUID publishedEvent(TestUsers.TestUser organizer, String title, OffsetDateTime start, String city,
            long pricePaise, Double lat, Double lng, String categorySlug) {
        UUID venue = jdbc.queryForObject("""
                insert into venues (organizer_id, name, address_line, city, state, pincode, lat, lng)
                values (?, 'Test Arena', '1 MG Road', ?, 'Karnataka', '560001', ?, ?) returning id
                """, UUID.class, organizer.organizerId(), city, lat, lng);
        UUID event = jdbc.queryForObject("""
                insert into events (organizer_id, category_id, venue_id, slug, title, summary, cover_image_url,
                                    starts_at, ends_at, status, published_at)
                values (?, ?, ?, ?, ?, 'A great night out', ?, ?, ?, 'PUBLISHED', now()) returning id
                """, UUID.class, organizer.organizerId(), categoryId(categorySlug), venue,
                "pub-" + UUID.randomUUID(), title, "https://res.cloudinary.com/demo/image/upload/stagepass/events/x/c.jpg",
                start, start.plusHours(3));
        ticketType(event, pricePaise, 100);
        return event;
    }

    public void setStatus(UUID eventId, String status) {
        jdbc.update("update events set status = ? where id = ?", status, eventId);
    }
}
