package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminDisputeRow;
import com.stagepass.admin.api.AdminPaymentRow;
import com.stagepass.admin.api.AdminRefundRow;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.LikePatterns;
import com.stagepass.organizer.service.CsvWriter;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payments, refunds and disputes for admins, plus the reconciliation CSV (spec 9, 18). */
@Service
public class AdminTransactionQueries {

    static final List<String> PAYMENT_STATUSES = List.of("CREATED", "AUTHORIZED", "CAPTURED", "FAILED", "REFUNDED");
    static final List<String> REFUND_STATUSES = List.of("REQUESTED", "PENDING_APPROVAL", "DECLINED", "PROCESSING",
            "PROCESSED", "FAILED");
    private static final List<String> CSV_HEADER = List.of("Date (IST)", "Booking code", "Event", "Attendee email",
            "Amount (Rs.)", "Status", "Method", "Razorpay payment id", "Razorpay order id", "Gateway fee (Rs.)",
            "Gateway tax (Rs.)", "Refunded (Rs.)");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final int EXPORT_FETCH_SIZE = 500;

    private static final String PAYMENT_FROM = """
            from payments p join bookings b on b.id = p.booking_id join events e on e.id = b.event_id where true
            """;

    private final NamedParameterJdbcTemplate sql;
    private final NamedParameterJdbcTemplate exportSql;
    private final AuditLogService audit;
    private final TransactionTemplate auditTx;

    public AdminTransactionQueries(NamedParameterJdbcTemplate sql, AuditLogService audit,
            PlatformTransactionManager txManager) {
        this.sql = sql;
        this.audit = audit;
        JdbcTemplate streaming = new JdbcTemplate(sql.getJdbcTemplate().getDataSource());
        streaming.setFetchSize(EXPORT_FETCH_SIZE);
        this.exportSql = new NamedParameterJdbcTemplate(streaming);
        this.auditTx = new TransactionTemplate(txManager);
        this.auditTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentRow> payments(String q, String status, LocalDate from, LocalDate to, Integer page,
            Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String body = PAYMENT_FROM + paymentFilters(q, status, from, to, p);
        return AdminQueries.page(sql, """
                select p.id, p.booking_id, b.code, e.title, b.attendee_email, p.amount_paise, p.status, p.method,
                       p.razorpay_payment_id, p.gateway_fee_paise, p.created_at
                """, body, "p.created_at desc, p.id", p, page, size, (rs, n) -> new AdminPaymentRow(
                rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("code"),
                rs.getString("title"), rs.getString("attendee_email"), rs.getLong("amount_paise"), rs.getString("status"),
                rs.getString("method"), rs.getString("razorpay_payment_id"), rs.getObject("gateway_fee_paise", Long.class),
                rs.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRefundRow> refunds(String status, String q, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder(
                "from refunds r join bookings b on b.id = r.booking_id join events e on e.id = b.event_id where true");
        if (AdminQueries.present(status)) {
            p.addValue("status", AdminQueries.oneOf(REFUND_STATUSES, status, "status"));
            body.append(" and r.status = :status");
        }
        if (AdminQueries.present(q)) {
            p.addValue("q", LikePatterns.contains(q));
            body.append(" and (lower(b.code) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(b.attendee_email) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(coalesce(r.razorpay_refund_id, '')) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(")");
        }
        return AdminQueries.page(sql, """
                select r.id, r.booking_id, b.code, e.title, r.kind, r.status, r.amount_paise, r.attempts, r.needs_retry,
                       r.failure_reason, r.created_at
                """, body.toString(), "r.created_at desc, r.id", p, page, size, (rs, n) -> new AdminRefundRow(
                rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("code"),
                rs.getString("title"), rs.getString("kind"), rs.getString("status"), rs.getLong("amount_paise"),
                rs.getInt("attempts"), rs.getBoolean("needs_retry"), rs.getString("failure_reason"),
                rs.getObject("created_at", OffsetDateTime.class)));
    }

    @Transactional(readOnly = true)
    public AdminRefundRow refund(UUID refundId) {
        PageResponse<AdminRefundRow> one = AdminQueries.page(sql, """
                select r.id, r.booking_id, b.code, e.title, r.kind, r.status, r.amount_paise, r.attempts, r.needs_retry,
                       r.failure_reason, r.created_at
                """, "from refunds r join bookings b on b.id = r.booking_id join events e on e.id = b.event_id"
                + " where r.id = :id", "r.id", new MapSqlParameterSource("id", refundId), 0, 1,
                (rs, n) -> new AdminRefundRow(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
                        rs.getString("code"), rs.getString("title"), rs.getString("kind"), rs.getString("status"),
                        rs.getLong("amount_paise"), rs.getInt("attempts"), rs.getBoolean("needs_retry"),
                        rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class)));
        return one.items().isEmpty() ? null : one.items().get(0);
    }

    /** Razorpay dispute statuses are free text (open, under_review, won, lost, closed): matched case-insensitively. */
    @Transactional(readOnly = true)
    public PageResponse<AdminDisputeRow> disputes(String status, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder("""
                from disputes d join payments p on p.id = d.payment_id join bookings b on b.id = p.booking_id
                join events e on e.id = b.event_id where true""");
        if (AdminQueries.present(status)) {
            p.addValue("status", status.strip().toLowerCase(java.util.Locale.ROOT));
            body.append(" and lower(d.status) = :status");
        }
        return AdminQueries.page(sql, """
                select d.id, d.razorpay_dispute_id, b.id as booking_id, b.code, e.title, d.amount_paise, d.reason,
                       d.status, d.created_at
                """, body.toString(), "d.created_at desc, d.id", p, page, size, (rs, n) -> new AdminDisputeRow(
                rs.getObject("id", UUID.class), rs.getString("razorpay_dispute_id"), rs.getObject("booking_id", UUID.class),
                rs.getString("code"), rs.getString("title"), rs.getLong("amount_paise"), rs.getString("reason"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)));
    }

    /** Both dates are required for an export (one CSV covers at most 366 days). */
    public static void requireExportRange(LocalDate from, LocalDate to) {
        FieldErrors.start().require(from, "from").require(to, "to").throwIfAny();
    }

    /** Streams the payments CSV; the audit row is written first in its own transaction. Returns the rows written. */
    @Transactional(readOnly = true)
    public int writePaymentsCsv(UUID adminId, LocalDate from, LocalDate to, String status, Writer out) {
        requireExportRange(from, to);
        MapSqlParameterSource p = new MapSqlParameterSource();
        String body = PAYMENT_FROM + paymentFilters(null, status, from, to, p);
        Integer count = sql.queryForObject("select count(*) " + body, p, Integer.class);
        int expected = count == null ? 0 : count;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.toString());
        details.put("to", to.toString());
        details.put("status", status);
        details.put("rows", expected);
        auditTx.executeWithoutResult(s -> audit.record(adminId, "TRANSACTIONS_EXPORTED", "payments", null, null, details));

        int[] rows = {0};
        write(out, CsvWriter.BOM + CsvWriter.row(CSV_HEADER));
        exportSql.query("""
                select p.created_at, b.code, e.title, b.attendee_email, p.amount_paise, p.status, p.method,
                       p.razorpay_payment_id, p.razorpay_order_id, p.gateway_fee_paise, p.gateway_tax_paise,
                       (select coalesce(sum(r.amount_paise), 0) from refunds r
                        where r.payment_id = p.id and r.status = 'PROCESSED') as refunded
                """ + body + " order by p.created_at, p.id", p, rs -> {
            write(out, CsvWriter.row(Arrays.asList(
                    CSV_TIME.format(rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(AdminQueries.IST)),
                    rs.getString("code"), rs.getString("title"), rs.getString("attendee_email"),
                    rupees(rs.getLong("amount_paise")), rs.getString("status"), rs.getString("method"),
                    rs.getString("razorpay_payment_id"), rs.getString("razorpay_order_id"),
                    rupees(rs.getObject("gateway_fee_paise", Long.class)), rupees(rs.getObject("gateway_tax_paise", Long.class)),
                    rupees(rs.getLong("refunded")))));
            rows[0]++;
        });
        return rows[0];
    }

    public static String csvFilename(LocalDate from, LocalDate to) {
        return "stagepass-payments-" + from + "-to-" + to + ".csv";
    }

    /** Rupees with exactly two decimals and no symbol ({@code 123450 → "1234.50"}); null → empty. */
    static String rupees(Long paise) {
        return paise == null ? "" : BigDecimal.valueOf(paise, 2).toPlainString();
    }

    private static String paymentFilters(String q, String status, LocalDate from, LocalDate to, MapSqlParameterSource p) {
        StringBuilder where = new StringBuilder();
        if (AdminQueries.present(q)) {
            p.addValue("q", LikePatterns.contains(q));
            where.append(" and (lower(b.code) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(b.attendee_email) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(coalesce(p.razorpay_payment_id, '')) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(p.razorpay_order_id) like :q").append(AdminQueries.LIKE_ESCAPE).append(")");
        }
        if (AdminQueries.present(status)) {
            p.addValue("status", AdminQueries.oneOf(PAYMENT_STATUSES, status, "status"));
            where.append(" and p.status = :status");
        }
        where.append(AdminQueries.dateRange("p.created_at", from, to, p));
        return where.toString();
    }

    private static void write(Writer out, String s) {
        try {
            out.write(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
