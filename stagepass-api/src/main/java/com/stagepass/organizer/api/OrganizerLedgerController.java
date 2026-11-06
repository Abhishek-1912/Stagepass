package com.stagepass.organizer.api;

import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.OrganizerGuard;
import com.stagepass.organizer.service.PayoutBalances;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Payouts page: balances, ledger entries and payouts for the signed-in organizer. */
@RestController
public class OrganizerLedgerController {

    private final PayoutBalances balances;
    private final NamedParameterJdbcTemplate sql;
    private final OrganizerGuard guard;

    public OrganizerLedgerController(PayoutBalances balances, NamedParameterJdbcTemplate sql, OrganizerGuard guard) {
        this.balances = balances;
        this.sql = sql;
        this.guard = guard;
    }

    @GetMapping("/api/v1/organizer/ledger")
    @Transactional(readOnly = true)
    public LedgerResponse ledger(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        UUID org = guard.requireApprovedOrganizerId();
        Pageable p = PageRequests.of(page, size, Sort.unsorted());
        MapSqlParameterSource params = new MapSqlParameterSource("org", org)
                .addValue("limit", p.getPageSize()).addValue("offset", p.getOffset());
        Long total = sql.queryForObject("select count(*) from organizer_ledger_entries where organizer_id = :org", params,
                Long.class);
        List<LedgerResponse.LedgerLine> lines = sql.query("""
                select l.id, l.type, l.amount_paise, l.event_id, e.title, b.code, l.note, l.created_at
                from organizer_ledger_entries l
                left join events e on e.id = l.event_id
                left join bookings b on b.id = l.booking_id
                where l.organizer_id = :org
                order by l.created_at desc, l.id
                limit :limit offset :offset
                """, params, (rs, n) -> new LedgerResponse.LedgerLine(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getLong("amount_paise"), rs.getObject("event_id", UUID.class), rs.getString("title"),
                rs.getString("code"), rs.getString("note"), rs.getObject("created_at", OffsetDateTime.class)));
        List<LedgerResponse.PayoutLine> payouts = sql.query("""
                select id, amount_paise, reference, paid_at, note from payouts where organizer_id = :org
                order by paid_at desc
                """, params, (rs, n) -> new LedgerResponse.PayoutLine(rs.getObject("id", UUID.class),
                rs.getLong("amount_paise"), rs.getString("reference"), rs.getObject("paid_at", OffsetDateTime.class),
                rs.getString("note")));
        long t = total == null ? 0 : total;
        return new LedgerResponse(balances.forOrganizer(org), new PageResponse<>(lines, p.getPageNumber(), p.getPageSize(), t,
                (int) ((t + p.getPageSize() - 1) / p.getPageSize())), payouts);
    }
}
