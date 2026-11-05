package com.stagepass.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Inserts minimal parent rows with plain SQL so schema tests do not depend on JPA entities. */
public final class SchemaFixtures {

    private final JdbcTemplate jdbc;

    public SchemaFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, full_name) values (?, ?, ?)", id, email, "Test User");
        return id;
    }

    public UUID organizer(UUID userId) {
        return jdbc.queryForObject("""
                insert into organizers (user_id, org_name, slug, contact_email, pan, status)
                values (?, 'Acme Live', ?, 'org@acme.test', 'ABCDE1234F', 'APPROVED') returning id
                """, UUID.class, userId, "acme-" + UUID.randomUUID());
    }

    public UUID categoryId(String slug) {
        return jdbc.queryForObject("select id from event_categories where slug = ?", UUID.class, slug);
    }

    public UUID event(UUID organizerId, String title) {
        OffsetDateTime start = OffsetDateTime.now().plusDays(10);
        return jdbc.queryForObject("""
                insert into events (organizer_id, category_id, slug, title, summary, starts_at, ends_at, status)
                values (?, ?, ?, ?, 'A great night', ?, ?, 'PUBLISHED') returning id
                """, UUID.class, organizerId, categoryId("music"), "ev-" + UUID.randomUUID(), title,
                start, start.plusHours(4));
    }

    public UUID ticketType(UUID eventId, long pricePaise, int total, int sold) {
        UUID id = jdbc.queryForObject("""
                insert into ticket_types (event_id, name, price_paise) values (?, 'General', ?) returning id
                """, UUID.class, eventId, pricePaise);
        jdbc.update("insert into ticket_inventory (ticket_type_id, total, sold, held) values (?, ?, ?, 0)",
                id, total, sold);
        return id;
    }

    public UUID confirmedBooking(UUID userId, UUID eventId) {
        return jdbc.queryForObject("""
                insert into bookings (code, user_id, event_id, status, subtotal_paise, fee_paise, fee_gst_paise,
                                      total_paise, commission_paise, attendee_name, attendee_email)
                values (?, ?, ?, 'CONFIRMED', 49900, 2247, 404, 52551, 0, 'Asha', 'asha@test.in') returning id
                """, UUID.class, "SP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), userId, eventId);
    }

    public UUID ticket(UUID bookingId, UUID ticketTypeId, UUID eventId) {
        UUID itemId = jdbc.queryForObject("""
                insert into booking_items (booking_id, ticket_type_id, quantity, unit_price_paise)
                values (?, ?, 1, 49900) returning id
                """, UUID.class, bookingId, ticketTypeId);
        return jdbc.queryForObject("""
                insert into tickets (booking_id, booking_item_id, ticket_type_id, event_id, code, holder_name, qr_nonce)
                values (?, ?, ?, ?, ?, 'Asha', 'nonce') returning id
                """, UUID.class, bookingId, itemId, ticketTypeId, eventId,
                "SP-" + UUID.randomUUID().toString().substring(0, 9).toUpperCase());
    }
}
