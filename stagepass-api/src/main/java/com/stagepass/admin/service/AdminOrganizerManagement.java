package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminOrganizerDetail;
import com.stagepass.admin.api.AdminOrganizerRow;
import com.stagepass.admin.api.PayoutRow;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.crypto.PayoutCrypto;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.common.text.LikePatterns;
import com.stagepass.common.validation.Formats;
import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.domain.OrganizerStatus;
import com.stagepass.identity.repository.OrganizerRepository;
import com.stagepass.organizer.service.PayoutBalances;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Managing organizers after approval (spec 9): list with balances, detail, suspend and reinstate, and admin edits of
 * name, PAN, GSTIN and commission. Suspension keeps the ORGANIZER role; {@code OrganizerGuard} already needs APPROVED.
 */
@Service
public class AdminOrganizerManagement {

    private final OrganizerRepository organizers;
    private final NamedParameterJdbcTemplate sql;
    private final PayoutBalances balances;
    private final AuditLogService audit;
    private final PayoutCrypto crypto;

    public AdminOrganizerManagement(OrganizerRepository organizers, NamedParameterJdbcTemplate sql,
            PayoutBalances balances, AuditLogService audit, PayoutCrypto crypto) {
        this.organizers = organizers;
        this.sql = sql;
        this.balances = balances;
        this.audit = audit;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrganizerRow> list(String q, String status, Integer page, Integer size) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder body = new StringBuilder("from organizers o where true");
        if (AdminQueries.present(q)) {
            p.addValue("q", LikePatterns.contains(q));
            body.append(" and (lower(o.org_name) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(o.contact_email) like :q").append(AdminQueries.LIKE_ESCAPE)
                    .append(" or lower(o.slug) like :q").append(AdminQueries.LIKE_ESCAPE).append(")");
        }
        if (AdminQueries.present(status)) {
            p.addValue("status", AdminQueries.enumName(OrganizerStatus.class, status, "status"));
            body.append(" and o.status = :status");
        }
        return AdminQueries.page(sql, """
                select o.id, o.org_name, o.slug, o.contact_email, o.status, o.applied_at,
                       (select count(*) from events e where e.organizer_id = o.id) as events_count
                """, body.toString(), "lower(o.org_name), o.id", p, page, size, (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            PayoutBalances.Balances b = balances.forOrganizer(id);
            return new AdminOrganizerRow(id, rs.getString("org_name"), rs.getString("slug"), rs.getString("contact_email"),
                    rs.getString("status"), rs.getInt("events_count"), b.payablePaise(), b.negative(),
                    rs.getObject("applied_at", OffsetDateTime.class));
        });
    }

    @Transactional(readOnly = true)
    public AdminOrganizerDetail detail(UUID organizerId) {
        return detail(find(organizerId));
    }

    @Transactional
    public AdminOrganizerDetail suspend(UUID adminId, UUID organizerId, String reason) {
        Organizer o = find(organizerId);
        if (o.getStatus() != OrganizerStatus.APPROVED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only approved organizers can be suspended.");
        }
        o.setStatus(OrganizerStatus.SUSPENDED);
        audit.record(adminId, "ORGANIZER_SUSPENDED", "organizer", o.getId().toString(), Map.of("status", "APPROVED"),
                Map.of("status", "SUSPENDED", "reason", reason.strip()));
        return detail(o);
    }

    @Transactional
    public AdminOrganizerDetail reinstate(UUID adminId, UUID organizerId) {
        Organizer o = find(organizerId);
        if (o.getStatus() != OrganizerStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only suspended organizers can be reinstated.");
        }
        o.setStatus(OrganizerStatus.APPROVED);
        audit.record(adminId, "ORGANIZER_REINSTATED", "organizer", o.getId().toString(), Map.of("status", "SUSPENDED"),
                Map.of("status", "APPROVED"));
        return detail(o);
    }

    /**
     * Partial update: a field left out is unchanged. {@code gstin} "" clears it; {@code commissionBpsOverride} null
     * goes back to the platform default. Audits only the fields that actually changed.
     */
    @Transactional
    public AdminOrganizerDetail update(UUID adminId, UUID organizerId, JsonNode body) {
        Organizer o = find(organizerId);
        if (body == null || !body.isObject()) {
            throw FieldErrors.single("body", "must be a JSON object");
        }
        FieldErrors errors = FieldErrors.start();
        String orgName = o.getOrgName();
        String pan = o.getPan();
        String gstin = o.getGstin();
        Integer commission = o.getCommissionBpsOverride();
        if (body.has("orgName")) {
            String v = Formats.trim(text(body, "orgName"));
            if (v == null || v.length() > 120) {
                errors.add("orgName", "must be 1 to 120 characters");
            } else {
                orgName = v;
            }
        }
        if (body.has("pan")) {
            String v = Formats.upper(text(body, "pan"));
            if (v == null || !v.matches(Formats.PAN)) {
                errors.add("pan", "must be a valid PAN like ABCDE1234F");
            } else {
                pan = v;
            }
        }
        if (body.has("gstin")) {
            String v = Formats.upper(text(body, "gstin"));
            if (v != null && !v.matches(Formats.GSTIN)) {
                errors.add("gstin", "must be a valid GSTIN");
            } else {
                gstin = v;
            }
        }
        if (body.has("commissionBpsOverride")) {
            JsonNode n = body.get("commissionBpsOverride");
            if (n.isNull()) {
                commission = null;
            } else if (!n.isIntegralNumber() || n.asInt() < 0 || n.asInt() > 5000) {
                errors.add("commissionBpsOverride", "must be between 0 and 5000 basis points");
            } else {
                commission = n.asInt();
            }
        }
        errors.throwIfAny();

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff("orgName", o.getOrgName(), orgName, before, after);
        diff("pan", o.getPan(), pan, before, after);
        diff("gstin", o.getGstin(), gstin, before, after);
        diff("commissionBpsOverride", o.getCommissionBpsOverride(), commission, before, after);
        if (!after.isEmpty()) {
            o.setOrgName(orgName);
            o.setPan(pan);
            o.setGstin(gstin);
            o.setCommissionBpsOverride(commission);
            audit.record(adminId, "ORGANIZER_UPDATED", "organizer", o.getId().toString(), before, after);
        }
        return detail(o);
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    private static void diff(String field, Object was, Object now, Map<String, Object> before, Map<String, Object> after) {
        if (!Objects.equals(was, now)) {
            before.put(field, was);
            after.put(field, now);
        }
    }

    private Organizer find(UUID organizerId) {
        return organizers.findById(organizerId).orElseThrow(() -> ApiException.notFound("Organizer"));
    }

    private AdminOrganizerDetail detail(Organizer o) {
        String masked = o.getPayoutAccountNumberEnc() == null ? null
                : PayoutCrypto.mask(crypto.decrypt(o.getPayoutAccountNumberEnc()));
        List<PayoutRow> payouts = sql.query("""
                select p.id, p.amount_paise, p.reference, p.paid_at, u.email, p.note
                from payouts p join users u on u.id = p.recorded_by
                where p.organizer_id = :org order by p.paid_at desc, p.created_at desc limit 20
                """, new MapSqlParameterSource("org", o.getId()), (rs, n) -> new PayoutRow(rs.getObject("id", UUID.class),
                rs.getLong("amount_paise"), rs.getString("reference"), rs.getObject("paid_at", OffsetDateTime.class),
                rs.getString("email"), rs.getString("note")));
        return new AdminOrganizerDetail(o.getId(), o.getUserId(), o.getOrgName(), o.getSlug(), o.getStatus().name(),
                o.getContactEmail(), o.getContactPhone(), o.getWebsite(), o.getPan(), o.getGstin(), o.getState(),
                o.getPayoutAccountName(), masked, o.getPayoutIfsc(), o.getCommissionBpsOverride(), o.getAppliedAt(),
                o.getReviewedAt(), balances.forOrganizer(o.getId()), payouts);
    }
}
