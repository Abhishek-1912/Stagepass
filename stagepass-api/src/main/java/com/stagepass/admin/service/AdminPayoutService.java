package com.stagepass.admin.service;

import com.stagepass.admin.api.OrganizerPayoutSummary;
import com.stagepass.admin.api.PayoutRow;
import com.stagepass.admin.api.RecordPayoutRequest;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.config.StagepassProperties;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.LikePatterns;
import com.stagepass.notification.service.EmailOutbox;
import com.stagepass.notification.service.EmailTemplate;
import com.stagepass.organizer.service.PayoutBalances;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recording bank payouts to organizers (spec 8.4a: payouts are made outside Stagepass and recorded here). A payout can't
 * exceed what is payable at that moment; the organizer row is locked so two admins can't both pay the same balance.
 */
@Service
public class AdminPayoutService {

    private final NamedParameterJdbcTemplate sql;
    private final PayoutBalances balances;
    private final AuditLogService audit;
    private final EmailOutbox outbox;
    private final Clock clock;
    private final int holdDays;

    public AdminPayoutService(NamedParameterJdbcTemplate sql, PayoutBalances balances, AuditLogService audit,
            EmailOutbox outbox, Clock clock, StagepassProperties props) {
        this.sql = sql;
        this.balances = balances;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
        this.holdDays = props.payout().holdDays();
    }

    /**
     * Balances per organizer computed in SQL with the same rule as {@link PayoutBalances} (an event's earnings are
     * payable {@code holdDays} after it ends, or at once if cancelled), so the list can sort and filter on them.
     */
    @Transactional(readOnly = true)
    public PageResponse<OrganizerPayoutSummary> summaries(String q, boolean onlyPayable, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource("now", OffsetDateTime.now(clock)).addValue("hold", holdDays);
        StringBuilder body = new StringBuilder("""
                from (
                    select o.id, o.org_name, o.status, o.contact_email,
                           coalesce(sum(l.amount_paise) filter (where l.type = 'PAYOUT' or l.event_id is null
                               or e.status = 'CANCELLED' or e.ends_at + make_interval(days => :hold) <= :now), 0) as payable,
                           coalesce(sum(l.amount_paise) filter (where l.type <> 'PAYOUT' and l.event_id is not null
                               and e.status <> 'CANCELLED' and e.ends_at + make_interval(days => :hold) > :now), 0) as pending,
                           coalesce(-sum(l.amount_paise) filter (where l.type = 'PAYOUT'), 0) as paid_out,
                           (select max(x.paid_at) from payouts x where x.organizer_id = o.id) as last_payout_at
                    from organizers o
                    left join organizer_ledger_entries l on l.organizer_id = o.id
                    left join events e on e.id = l.event_id
                    where o.status in ('APPROVED', 'SUSPENDED')
                    group by o.id
                ) s where true""");
        if (AdminQueries.present(q)) {
            p.addValue("q", LikePatterns.contains(q));
            body.append(" and (lower(s.org_name) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(s.contact_email) like :q").append(AdminQueries.LIKE_ESCAPE).append(")");
        }
        if (onlyPayable) {
            body.append(" and s.payable > 0");
        }
        return AdminQueries.page(sql, "select s.*", body.toString(), "s.payable desc, lower(s.org_name), s.id", p, page,
                size, (rs, n) -> new OrganizerPayoutSummary(rs.getObject("id", UUID.class), rs.getString("org_name"),
                        rs.getString("status"), rs.getLong("pending"), rs.getLong("payable"), rs.getLong("paid_out"),
                        rs.getLong("payable") < 0, rs.getObject("last_payout_at", OffsetDateTime.class)));
    }

    @Transactional
    public PayoutRow record(UUID adminId, UUID organizerId, RecordPayoutRequest r) {
        List<Map<String, Object>> org = sql.queryForList(
                "select id, org_name, contact_email from organizers where id = :id for update",
                new MapSqlParameterSource("id", organizerId));
        if (org.isEmpty()) {
            throw ApiException.notFound("Organizer");
        }
        String reference = r.reference().strip();
        long amount = r.amountPaise();
        long payable = balances.forOrganizer(organizerId).payablePaise();
        FieldErrors errors = FieldErrors.start();
        if (reference.length() < 3 || reference.length() > 100) {
            errors.add("reference", "must be 3 to 100 characters");
        }
        if (amount <= 0) {
            errors.add("amountPaise", "must be more than zero");
        } else if (amount > payable) {
            errors.add("amountPaise", "is more than the payable balance (Rs. "
                    + BigDecimal.valueOf(Math.max(payable, 0), 2).toPlainString() + ")");
        }
        if (r.paidOn().isAfter(LocalDate.now(clock.withZone(AdminQueries.IST)))) {
            errors.add("paidOn", "can't be in the future");
        }
        Boolean taken = sql.queryForObject(
                "select exists (select 1 from payouts where organizer_id = :org and lower(reference) = lower(:ref))",
                new MapSqlParameterSource("org", organizerId).addValue("ref", reference), Boolean.class);
        if (Boolean.TRUE.equals(taken)) {
            errors.add("reference", "was already recorded");
        }
        errors.throwIfAny();

        OffsetDateTime paidAt = r.paidOn().atTime(LocalTime.NOON).atZone(AdminQueries.IST).toOffsetDateTime();
        String note = r.note() == null || r.note().isBlank() ? null : r.note().strip();
        UUID payoutId = sql.queryForObject("""
                insert into payouts (organizer_id, amount_paise, reference, paid_at, recorded_by, note)
                values (:org, :amount, :ref, :paidAt, :admin, :note) returning id
                """, new MapSqlParameterSource("org", organizerId).addValue("amount", amount).addValue("ref", reference)
                .addValue("paidAt", paidAt).addValue("admin", adminId).addValue("note", note), UUID.class);
        sql.update("""
                insert into organizer_ledger_entries (organizer_id, type, amount_paise, note)
                values (:org, 'PAYOUT', :amount, :note)
                """, new MapSqlParameterSource("org", organizerId).addValue("amount", -amount)
                .addValue("note", "Payout " + reference));

        Map<String, Object> email = new LinkedHashMap<>();
        email.put("orgName", org.get(0).get("org_name"));
        email.put("amountPaise", amount);
        email.put("reference", reference);
        email.put("paidOn", r.paidOn().toString());
        outbox.enqueue((String) org.get(0).get("contact_email"), EmailTemplate.PAYOUT_RECORDED, email,
                "payout-recorded:" + payoutId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("payoutId", payoutId.toString());
        details.put("amountPaise", amount);
        details.put("reference", reference);
        details.put("paidOn", r.paidOn().toString());
        audit.record(adminId, "PAYOUT_RECORDED", "organizer", organizerId.toString(), null, details);

        return sql.queryForObject("""
                select p.id, p.amount_paise, p.reference, p.paid_at, u.email, p.note
                from payouts p join users u on u.id = p.recorded_by where p.id = :id
                """, new MapSqlParameterSource("id", payoutId), (rs, n) -> new PayoutRow(rs.getObject("id", UUID.class),
                rs.getLong("amount_paise"), rs.getString("reference"), rs.getObject("paid_at", OffsetDateTime.class),
                rs.getString("email"), rs.getString("note")));
    }
}
