package com.stagepass.organizer.service;

import com.stagepass.booking.domain.BookingStatus;
import com.stagepass.booking.domain.TicketStatus;
import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.LikePatterns;
import com.stagepass.organizer.api.AttendeeRow;
import com.stagepass.organizer.api.OrganizerBookingRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bookings and attendee lists of one of the organizer's events, and the attendee CSV export. */
@Service
@Transactional(readOnly = true)
public class OrganizerBookingQueries {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final List<String> CSV_HEADER = List.of("Ticket code", "Ticket type", "Holder name", "Booking code",
            "Email", "Phone", "Status", "Booked at (IST)", "Checked in at (IST)");

    /** An event the organizer owns, for headers and filenames. */
    public record OwnedEvent(UUID id, String slug, String title) {
    }

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;
    private final Clock clock;
    /** Same data source, but rows arrive in batches of 500 through a cursor instead of all at once. */
    private final NamedParameterJdbcTemplate exportSql;
    private final TransactionTemplate auditTx;

    public OrganizerBookingQueries(NamedParameterJdbcTemplate sql, AuditLogService audit, Clock clock,
            PlatformTransactionManager txManager) {
        this.sql = sql;
        this.audit = audit;
        this.clock = clock;
        JdbcTemplate streaming = new JdbcTemplate(sql.getJdbcTemplate().getDataSource());
        streaming.setFetchSize(EXPORT_FETCH_SIZE);
        this.exportSql = new NamedParameterJdbcTemplate(streaming);
        this.auditTx = new TransactionTemplate(txManager);
        this.auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static final int EXPORT_FETCH_SIZE = 500;

    /** Fetch size of the export cursor (tests check the export streams). */
    public int exportFetchSize() {
        return exportSql.getJdbcTemplate().getFetchSize();
    }

    public OwnedEvent owned(UUID organizerId, UUID eventId) {
        List<OwnedEvent> found = sql.query("select id, slug, title from events where id = :event and organizer_id = :org",
                new MapSqlParameterSource("event", eventId).addValue("org", organizerId),
                (rs, n) -> new OwnedEvent(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title")));
        return found.stream().findFirst().orElseThrow(() -> ApiException.notFound("Event"));
    }

    public PageResponse<OrganizerBookingRow> bookings(UUID organizerId, UUID eventId, String status, String q,
            Integer page, Integer size) {
        owned(organizerId, eventId);
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId);
        StringBuilder where = new StringBuilder(" from bookings b where b.event_id = :event");
        if (status == null || status.isBlank()) {
            where.append(" and b.status <> 'EXPIRED'");
        } else {
            p.addValue("status", enumName(BookingStatus.class, status, "status"));
            where.append(" and b.status = :status");
        }
        if (q != null && !q.isBlank()) {
            p.addValue("q", LikePatterns.contains(q));
            where.append(" and (lower(b.code) like :q or lower(b.attendee_name) like :q or lower(b.attendee_email) like :q)");
        }
        RowMapper<OrganizerBookingRow> mapper = (rs, n) -> new OrganizerBookingRow(rs.getObject("id", UUID.class),
                rs.getString("code"), rs.getString("status"), rs.getString("attendee_name"),
                rs.getString("attendee_email"), rs.getString("attendee_phone"), rs.getInt("tickets"),
                rs.getLong("total_paise"), rs.getLong("subtotal_paise"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("confirmed_at", OffsetDateTime.class));
        return page(where.toString(), """
                select b.id, b.code, b.status, b.attendee_name, b.attendee_email, b.attendee_phone, b.total_paise,
                       b.subtotal_paise, b.created_at, b.confirmed_at,
                       (select coalesce(sum(i.quantity), 0) from booking_items i where i.booking_id = b.id) as tickets
                """, " order by b.created_at desc, b.code", p, mapper, page, size);
    }

    public PageResponse<AttendeeRow> attendees(UUID organizerId, UUID eventId, String status, String q, Integer page,
            Integer size) {
        owned(organizerId, eventId);
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId);
        StringBuilder where = new StringBuilder(ATTENDEE_FROM + " where t.event_id = :event");
        if (status == null || status.isBlank()) {
            where.append(" and t.status in ('VALID', 'CHECKED_IN')");
        } else {
            p.addValue("status", enumName(TicketStatus.class, status, "status"));
            where.append(" and t.status = :status");
        }
        if (q != null && !q.isBlank()) {
            p.addValue("q", LikePatterns.contains(q));
            where.append(" and (lower(t.code) like :q or lower(b.code) like :q or lower(t.holder_name) like :q"
                    + " or lower(b.attendee_email) like :q)");
        }
        return page(where.toString(), ATTENDEE_SELECT, " order by b.confirmed_at, t.code", p, ATTENDEE, page, size);
    }

    private static final String EXPORT_WHERE =
            " where t.event_id = :event and b.status in ('CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED')";

    /**
     * Writes every ticket of the event's sold bookings as CSV (BOM, header, CRLF), streaming rows through a cursor.
     * The export is audited first, in its own transaction, so it stays on record even if the download breaks off.
     */
    @Transactional(readOnly = true)
    public int writeAttendeesCsv(UUID organizerId, UUID actorId, UUID eventId, Writer out) {
        owned(organizerId, eventId);
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId);
        Integer count = sql.queryForObject("select count(*)" + ATTENDEE_FROM + EXPORT_WHERE, p, Integer.class);
        int expected = count == null ? 0 : count;
        auditTx.executeWithoutResult(s -> audit.record(actorId, "ATTENDEES_EXPORTED", "event", eventId.toString(), null,
                Map.of("rows", expected)));

        int[] rows = {0};
        write(out, CsvWriter.BOM + CsvWriter.row(CSV_HEADER));
        exportSql.query(ATTENDEE_SELECT + ATTENDEE_FROM + EXPORT_WHERE + " order by b.confirmed_at, t.code", p, rs -> {
            AttendeeRow r = ATTENDEE.mapRow(rs, rows[0]);
            write(out, CsvWriter.row(Arrays.asList(r.ticketCode(), r.ticketType(), r.holderName(), r.bookingCode(),
                    r.attendeeEmail(), r.attendeePhone(), r.status(), ist(r.bookedAt()), ist(r.checkedInAt()))));
            rows[0]++;
        });
        return rows[0];
    }

    /** {@code attendees-{slug}-{yyyyMMdd}.csv}, dated in IST. */
    public String csvFilename(OwnedEvent e) {
        return "attendees-" + e.slug() + "-" + LocalDate.now(clock.withZone(OrganizerAnalytics.IST))
                .format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    }

    private static final String ATTENDEE_SELECT = """
            select t.id, t.code, tt.name as type_name, t.holder_name, b.code as booking_code, b.attendee_email,
                   b.attendee_phone, t.status, b.confirmed_at, c.scanned_at
            """;
    private static final String ATTENDEE_FROM = """
             from tickets t
             join bookings b on b.id = t.booking_id
             join ticket_types tt on tt.id = t.ticket_type_id
             left join check_ins c on c.ticket_id = t.id
            """;
    private static final RowMapper<AttendeeRow> ATTENDEE = (rs, n) -> new AttendeeRow(rs.getObject("id", UUID.class),
            rs.getString("code"), rs.getString("type_name"), rs.getString("holder_name"), rs.getString("booking_code"),
            rs.getString("attendee_email"), rs.getString("attendee_phone"), rs.getString("status"),
            rs.getObject("confirmed_at", OffsetDateTime.class), rs.getObject("scanned_at", OffsetDateTime.class));

    private <T> PageResponse<T> page(String where, String select, String order, MapSqlParameterSource p,
            RowMapper<T> mapper, Integer page, Integer size) {
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        Long total = sql.queryForObject("select count(*)" + where, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<T> items = sql.query(select + where + order + " limit :limit offset :offset", p, mapper);
        long t = total == null ? 0 : total;
        return new PageResponse<>(items, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }

    private static <E extends Enum<E>> String enumName(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw FieldErrors.single(field, "is not a valid status");
        }
    }

    private static String ist(OffsetDateTime t) {
        return t == null ? "" : CSV_TIME.format(t.atZoneSameInstant(OrganizerAnalytics.IST));
    }

    private static void write(Writer out, String s) {
        try {
            out.write(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
