package com.stagepass.catalog;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import com.stagepass.catalog.domain.TicketType;
import com.stagepass.catalog.repository.CategoryRepository;
import com.stagepass.catalog.repository.EventRepository;
import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.repository.OrganizerRepository;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SchemaFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CatalogMappingTest extends PostgresIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    CategoryRepository categories;

    @Autowired
    OrganizerRepository organizers;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    private Event newEvent() {
        SchemaFixtures fx = new SchemaFixtures(jdbc);
        UUID organizerId = fx.organizer(fx.user("map-" + UUID.randomUUID() + "@test.in"));
        Event event = new Event();
        event.setOrganizer(organizers.findById(organizerId).orElseThrow());
        event.setCategory(categories.findBySlug("music").orElseThrow());
        event.setSlug("map-test-" + UUID.randomUUID());
        event.setTitle("Mapping Night");
        event.setStartsAt(OffsetDateTime.now().plusDays(10));
        event.setEndsAt(OffsetDateTime.now().plusDays(10).plusHours(3));
        return event;
    }

    @Test
    void persistsEventWithTicketTypeAndInventory() {
        Event event = newEvent();
        TicketType general = TicketType.create(event, "General", 49900L, 100);
        event.getTicketTypes().add(general);
        events.saveAndFlush(event);
        em.clear();

        Event reloaded = events.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(reloaded.getGallery()).isEmpty();
        assertThat(reloaded.getTicketTypes()).hasSize(1);
        assertThat(reloaded.getTicketTypes().get(0).getInventory().getTotal()).isEqualTo(100);
        assertThat(reloaded.getTicketTypes().get(0).getPricePaise()).isEqualTo(49900L);
        assertThat(reloaded.isOnline()).isFalse();
    }

    @Test
    void eventGalleryRoundTripsAsJson() {
        Event event = newEvent();
        event.setGallery(List.of("https://res.cloudinary.com/x/image/upload/a.jpg"));
        events.saveAndFlush(event);
        em.clear();

        assertThat(events.findById(event.getId()).orElseThrow().getGallery())
                .containsExactly("https://res.cloudinary.com/x/image/upload/a.jpg");
    }

    @Test
    void organizerStatusDefaultsToPending() {
        UUID userId = new SchemaFixtures(jdbc).user("org-" + UUID.randomUUID() + "@test.in");
        Organizer organizer = new Organizer();
        organizer.setUserId(userId);
        organizer.setOrgName("Acme");
        organizer.setSlug("acme-" + UUID.randomUUID());
        organizer.setContactEmail("acme@test.in");
        organizer.setPan("ABCDE1234F");
        organizers.saveAndFlush(organizer);
        em.clear();

        assertThat(organizers.findByUserId(userId).orElseThrow().getStatus()).isEqualTo(OrganizerStatus.PENDING);
    }
}
