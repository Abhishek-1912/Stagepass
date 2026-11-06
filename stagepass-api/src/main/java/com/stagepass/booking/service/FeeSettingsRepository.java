package com.stagepass.booking.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Reads fee rules: platform defaults from {@code platform_settings['fees']} (cached 60 s), then per-event
 * convenience-fee override ({@code {"bps":…,"flatPaise":…}}), then event → organizer commission overrides.
 */
@Component
public class FeeSettingsRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String KEY = "fees";

    private final JdbcTemplate jdbc;
    private final Cache<String, FeeSettings> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).build();

    public FeeSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FeeSettings current() {
        return cache.get(KEY, k -> {
            String json = jdbc.queryForObject("select value::text from platform_settings where key = ?", String.class, k);
            JsonNode n = JSON.readTree(json);
            return new FeeSettings(n.path("convenienceFeeBps").asInt(250), n.path("convenienceFeeFlatPaise").asLong(1000),
                    n.path("gstBps").asInt(1800), n.path("commissionBps").asInt(0),
                    n.path("refundFeeOnAttendeeRequest").asBoolean(false));
        });
    }

    public void evict() {
        cache.invalidateAll();
    }

    public FeeSettings forEvent(UUID eventId) {
        FeeSettings base = current();
        Map<String, Object> row = jdbc.queryForMap("""
                select e.convenience_fee_override::text as fee_override, e.commission_bps_override as event_commission,
                       o.commission_bps_override as organizer_commission
                from events e join organizers o on o.id = e.organizer_id where e.id = ?
                """, eventId);
        int bps = base.convenienceFeeBps();
        long flat = base.convenienceFeeFlatPaise();
        if (row.get("fee_override") != null) {
            JsonNode o = JSON.readTree((String) row.get("fee_override"));
            bps = o.path("bps").asInt(bps);
            flat = o.path("flatPaise").asLong(flat);
        }
        int commission = row.get("event_commission") != null ? ((Number) row.get("event_commission")).intValue()
                : row.get("organizer_commission") != null ? ((Number) row.get("organizer_commission")).intValue()
                : base.commissionBps();
        return new FeeSettings(bps, flat, base.gstBps(), commission, base.refundFeeOnAttendeeRequest());
    }
}
