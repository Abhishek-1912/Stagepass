package com.stagepass.checkin.service;

import com.stagepass.checkin.api.ScanRequest;
import com.stagepass.checkin.api.ScanResponse;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.ticketing.service.QrClaims;
import com.stagepass.ticketing.service.QrTokens;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Scans at the door (spec 8.6). The answer is decided in a fixed order — genuine ticket, right event, still valid,
 * allowed at this gate — and entry itself by inserting into {@code check_ins}, whose unique ticket id lets exactly one
 * scan win across all gates and scanners. Every scan is logged.
 */
@Service
public class CheckInService {

    public static final String VALID = "VALID";
    public static final String ALREADY_USED = "ALREADY_USED";
    public static final String INVALID = "INVALID";
    public static final String WRONG_EVENT = "WRONG_EVENT";
    public static final String WRONG_GATE = "WRONG_GATE";

    /** What the door needs to know about a ticket. */
    record TicketRow(UUID id, UUID eventId, String eventTitle, String status, String bookingStatus, String nonce,
            String code, String holderName, UUID ticketTypeId, String ticketType) {
    }

    private static final RowMapper<TicketRow> TICKET = (rs, n) -> new TicketRow(rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class), rs.getString("event_title"), rs.getString("status"),
            rs.getString("booking_status"), rs.getString("qr_nonce"), rs.getString("code"), rs.getString("holder_name"),
            rs.getObject("ticket_type_id", UUID.class), rs.getString("type_name"));

    /** Read with {@code for update of t}: a refund or another scan holding the ticket is waited for, then seen. */
    private static final String TICKET_SELECT = """
            select t.id, t.event_id, e.title as event_title, t.status, b.status as booking_status, t.qr_nonce, t.code,
                   t.holder_name, t.ticket_type_id, tt.name as type_name
            from tickets t
            join bookings b on b.id = t.booking_id
            join events e on e.id = t.event_id
            join ticket_types tt on tt.id = t.ticket_type_id
            """;

    private final NamedParameterJdbcTemplate sql;
    private final ScanAccess access;
    private final QrTokens qrTokens;
    private final Clock clock;

    public CheckInService(NamedParameterJdbcTemplate sql, ScanAccess access, QrTokens qrTokens, Clock clock) {
        this.sql = sql;
        this.access = access;
        this.qrTokens = qrTokens;
        this.clock = clock;
    }

    @Transactional
    public ScanResponse scan(UUID userId, ScanRequest r) {
        boolean hasToken = r.token() != null;
        boolean hasCode = r.code() != null;
        if (hasToken == hasCode) {
            throw FieldErrors.single("token", "send either the QR token or the ticket code");
        }
        ScanAccess.ScannableEvent event = access.requireScannable(userId, r.eventId());
        requireOpen(event);
        UUID gateId = r.gateId();
        if (gateId != null) {
            requireGateOfEvent(gateId, event.eventId());
        }

        Optional<TicketRow> found = hasToken ? byToken(r.token()) : byCode(r.code());
        if (found.isEmpty()) {
            return answer(event, userId, gateId, hasCode, null, INVALID, "Not a valid Stagepass ticket", null, null, null);
        }
        TicketRow t = found.get();
        if (!t.eventId().equals(event.eventId())) {
            return answer(event, userId, gateId, hasCode, t, WRONG_EVENT, "This ticket is for " + t.eventTitle(), null, null,
                    null);
        }
        if ("REFUNDED".equals(t.status())) {
            return answer(event, userId, gateId, hasCode, t, INVALID, "This ticket was refunded", info(t), null, null);
        }
        if ("CANCELLED".equals(t.status())
                || !("CONFIRMED".equals(t.bookingStatus()) || "PARTIALLY_REFUNDED".equals(t.bookingStatus()))) {
            return answer(event, userId, gateId, hasCode, t, INVALID, "This ticket was cancelled", info(t), null, null);
        }
        if (gateId != null) {
            List<String> allowed = gatesAllowing(event.eventId(), t.ticketTypeId());
            if (!allowedHere(gateId, t.ticketTypeId())) {
                String where = allowed.isEmpty() ? "No gate accepts this ticket type" : "Use " + String.join(" or ", allowed);
                return answer(event, userId, gateId, hasCode, t, WRONG_GATE, "Wrong gate. " + where, info(t), null, allowed);
            }
        }

        List<UUID> inserted = sql.queryForList("""
                insert into check_ins (ticket_id, event_id, scanned_by, gate_id) values (:ticket, :event, :user, :gate)
                on conflict (ticket_id) do nothing returning id
                """, new MapSqlParameterSource("ticket", t.id()).addValue("event", event.eventId()).addValue("user", userId)
                .addValue("gate", gateId), UUID.class);
        if (!inserted.isEmpty()) {
            sql.update("update tickets set status = 'CHECKED_IN', version = version + 1 where id = :ticket and status = 'VALID'",
                    new MapSqlParameterSource("ticket", t.id()));
            return answer(event, userId, gateId, hasCode, t, VALID, "Welcome in", info(t), null, null);
        }
        return answer(event, userId, gateId, hasCode, t, ALREADY_USED, "Already used", info(t), firstScan(t.id(), userId),
                null);
    }

    private void requireOpen(ScanAccess.ScannableEvent e) {
        boolean live = "PUBLISHED".equals(e.status());
        boolean justEnded = "COMPLETED".equals(e.status())
                && e.endsAt().isAfter(OffsetDateTime.now(clock).minusHours(12));
        if (!live && !justEnded) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Check-in is closed for this event");
        }
    }

    private void requireGateOfEvent(UUID gateId, UUID eventId) {
        Integer n = sql.queryForObject("select count(*) from gates where id = :gate and event_id = :event",
                new MapSqlParameterSource("gate", gateId).addValue("event", eventId), Integer.class);
        if (n == null || n == 0) {
            throw FieldErrors.single("gateId", "is not a gate of this event");
        }
    }

    /** A gate with no ticket-type list accepts every type. */
    private boolean allowedHere(UUID gateId, UUID ticketTypeId) {
        Boolean ok = sql.queryForObject("""
                select not exists (select 1 from gate_ticket_types where gate_id = :gate)
                    or exists (select 1 from gate_ticket_types where gate_id = :gate and ticket_type_id = :type)
                """, new MapSqlParameterSource("gate", gateId).addValue("type", ticketTypeId), Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    private List<String> gatesAllowing(UUID eventId, UUID ticketTypeId) {
        return sql.queryForList("""
                select g.name from gates g
                where g.event_id = :event
                  and (not exists (select 1 from gate_ticket_types x where x.gate_id = g.id)
                       or exists (select 1 from gate_ticket_types x where x.gate_id = g.id and x.ticket_type_id = :type))
                order by g.sort_order, g.name
                """, new MapSqlParameterSource("event", eventId).addValue("type", ticketTypeId), String.class);
    }

    private Optional<TicketRow> byToken(String token) {
        Optional<QrClaims> claims = qrTokens.verify(token.strip());
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        return sql.query(TICKET_SELECT + " where t.id = :id for update of t", new MapSqlParameterSource("id", claims.get().ticketId()), TICKET)
                .stream().findFirst()
                .filter(t -> t.nonce().equals(claims.get().nonce()) && t.eventId().equals(claims.get().eventId()));
    }

    private Optional<TicketRow> byCode(String code) {
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return sql.query(TICKET_SELECT + " where t.code = :code for update of t", new MapSqlParameterSource("code", normalized), TICKET)
                .stream().findFirst();
    }

    private ScanResponse.FirstScan firstScan(UUID ticketId, UUID userId) {
        return sql.query("""
                select c.scanned_at, c.scanned_by, g.name as gate_name, coalesce(u.full_name, u.email) as scanner
                from check_ins c
                left join gates g on g.id = c.gate_id
                join users u on u.id = c.scanned_by
                where c.ticket_id = :ticket
                """, new MapSqlParameterSource("ticket", ticketId), (rs, n) -> new ScanResponse.FirstScan(
                rs.getObject("scanned_at", OffsetDateTime.class), rs.getString("gate_name"), rs.getString("scanner"),
                userId.equals(rs.getObject("scanned_by", UUID.class)))).stream().findFirst().orElse(null);
    }

    private static ScanResponse.TicketInfo info(TicketRow t) {
        return new ScanResponse.TicketInfo(t.code(), t.holderName(), t.ticketType());
    }

    /** Logs the scan and builds the response with fresh counts. */
    private ScanResponse answer(ScanAccess.ScannableEvent event, UUID userId, UUID gateId, boolean manual, TicketRow t,
            String result, String message, ScanResponse.TicketInfo ticket, ScanResponse.FirstScan first,
            List<String> allowedGates) {
        sql.update("""
                insert into scan_logs (event_id, scanned_by, result, ticket_id, gate_id, manual)
                values (:event, :user, :result, :ticket, :gate, :manual)
                """, new MapSqlParameterSource("event", event.eventId()).addValue("user", userId).addValue("result", result)
                .addValue("ticket", t == null ? null : t.id()).addValue("gate", gateId).addValue("manual", manual));
        ScanResponse.Counts counts = sql.queryForObject("""
                select count(*) filter (where status = 'CHECKED_IN') as checked_in,
                       count(*) filter (where status in ('VALID', 'CHECKED_IN')) as total
                from tickets where event_id = :event
                """, new MapSqlParameterSource("event", event.eventId()),
                (rs, n) -> new ScanResponse.Counts(rs.getInt("checked_in"), rs.getInt("total")));
        return new ScanResponse(result, message, ticket, first, allowedGates, counts);
    }
}
