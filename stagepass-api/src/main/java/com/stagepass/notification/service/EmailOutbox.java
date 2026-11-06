package com.stagepass.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Queues an email in {@code email_outbox}. Joins the caller's transaction, so the email is sent only if the
 * business change (sign-up, booking, refund…) commits — and an email outage never rolls that change back.
 */
@Service
public class EmailOutbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EmailOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public UUID enqueue(String to, EmailTemplate template, Map<String, ?> data) {
        return jdbc.queryForObject("""
                insert into email_outbox (to_address, template, payload) values (?, ?, cast(? as jsonb)) returning id
                """, UUID.class, to, template.name(), mapper.writeValueAsString(data));
    }

    /**
     * Queues an email once per {@code dedupeKey} (e.g. {@code booking-confirmed:<id>}); returns empty when an email
     * with that key was already queued. Call inside the business transaction.
     */
    public Optional<UUID> enqueue(String to, EmailTemplate template, Map<String, ?> data, String dedupeKey) {
        java.util.List<UUID> ids = jdbc.queryForList("""
                insert into email_outbox (to_address, template, payload, dedupe_key) values (?, ?, cast(? as jsonb), ?)
                on conflict (dedupe_key) do nothing returning id
                """, UUID.class, to, template.name(), mapper.writeValueAsString(data), dedupeKey);
        return ids.stream().findFirst();
    }
}
