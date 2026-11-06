package com.stagepass.schema;

import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.SchemaFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class SchemaConstraintsTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    SchemaFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new SchemaFixtures(jdbc);
    }

    @Test
    void inventoryCannotOversell() {
        UUID org = fx.organizer(fx.user("o1@test.in"));
        UUID type = fx.ticketType(fx.event(org, "Sold Out Show"), 49900, 2, 2);

        assertThatThrownBy(() -> jdbc.update("update ticket_inventory set held = 1 where ticket_type_id = ?", type))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkInIsUniquePerTicket() {
        UUID user = fx.user("a1@test.in");
        UUID event = fx.event(fx.organizer(fx.user("o2@test.in")), "Gig");
        UUID type = fx.ticketType(event, 49900, 10, 1);
        UUID ticket = fx.ticket(fx.confirmedBooking(user, event), type, event);

        jdbc.update("insert into check_ins (ticket_id, event_id, scanned_by) values (?, ?, ?)", ticket, event, user);

        assertThatThrownBy(() -> jdbc.update(
                "insert into check_ins (ticket_id, event_id, scanned_by) values (?, ?, ?)", ticket, event, user))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void seedsRolesAndCategories() {
        assertThat(jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from event_categories where slug in ('music','concerts','technology','business','workshops','sports','comedy','festivals','food-lifestyle')", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "select (value->>'convenienceFeeBps')::int from platform_settings where key = 'fees'", Integer.class))
                .isEqualTo(250);
    }

    @Test
    void searchVectorIsGenerated() {
        fx.event(fx.organizer(fx.user("o3@test.in")), "Sunburn Goa");

        Integer hits = jdbc.queryForObject(
                "select count(*) from events where search_vector @@ plainto_tsquery('simple', 'sunburn')", Integer.class);
        assertThat(hits).isEqualTo(1);
    }

    @Test
    void moneyColumnsAreBigint() {
        String type = jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_name = 'bookings' and column_name = 'total_paise'
                """, String.class);
        assertThat(type).isEqualTo("bigint");
    }

    @Test
    void usersEmailIsOptionalButUnique() {
        jdbc.update("insert into users (id) values (?)", UUID.randomUUID());
        fx.user("dup@test.in");

        assertThatThrownBy(() -> fx.user("dup@test.in")).isInstanceOf(DuplicateKeyException.class);
    }
}
