package com.stagepass.checkin.service;

import com.stagepass.checkin.api.CheckInStatsResponse;
import com.stagepass.checkin.api.ScanLogRow;
import com.stagepass.common.api.PageRequests;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Live check-in counts (organizer and door staff) and the full scan log (organizer only). */
@Service
public class CheckInStats {

    private static final Set<String> RESULTS = Set.of(CheckInService.VALID, CheckInService.ALREADY_USED,
            CheckInService.INVALID, CheckInService.WRONG_EVENT, CheckInService.WRONG_GATE);

    private final NamedParameterJdbcTemplate sql;
    private final ScanAccess access;

    public CheckInStats(NamedParameterJdbcTemplate sql, ScanAccess access) {
        this.sql = sql;
        this.access = access;
    }

    @Transactional
    public CheckInStatsResponse stats(UUID userId, UUID eventId) {
        access.requireScannable(userId, eventId);
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId);
        int[] counts = sql.queryForObject("""
                select count(*) filter (where status = 'CHECKED_IN'), count(*) filter (where status in ('VALID', 'CHECKED_IN'))
                from tickets where event_id = :event
                """, p, (rs, n) -> new int[] {rs.getInt(1), rs.getInt(2)});
        List<CheckInStatsResponse.GateCount> byGate = new ArrayList<>(sql.query("""
                select g.id, g.name, (select count(*) from check_ins c where c.gate_id = g.id) as n
                from gates g where g.event_id = :event order by g.sort_order, g.name
                """, p, (rs, n) -> new CheckInStatsResponse.GateCount(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getInt("n"))));
        Integer noGate = sql.queryForObject("select count(*) from check_ins where event_id = :event and gate_id is null", p,
                Integer.class);
        if (noGate != null && noGate > 0) {
            byGate.add(new CheckInStatsResponse.GateCount(null, "No gate", noGate));
        }
        List<CheckInStatsResponse.ScannerCount> byScanner = sql.query("""
                select coalesce(u.full_name, u.email) as name, count(*) as n
                from check_ins c join users u on u.id = c.scanned_by
                where c.event_id = :event
                group by u.id, u.full_name, u.email
                order by n desc, name
                """, p, (rs, n) -> new CheckInStatsResponse.ScannerCount(rs.getString("name"), rs.getInt("n")));
        OffsetDateTime last = sql.queryForObject("select max(at) from scan_logs where event_id = :event", p,
                OffsetDateTime.class);
        return new CheckInStatsResponse(counts[0], counts[1], byGate, byScanner, last);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScanLogRow> scanLog(UUID organizerId, UUID eventId, String result, Integer page, Integer size) {
        Integer owned = sql.queryForObject("select count(*) from events where id = :event and organizer_id = :org",
                new MapSqlParameterSource("event", eventId).addValue("org", organizerId), Integer.class);
        if (owned == null || owned == 0) {
            throw ApiException.notFound("Event");
        }
        MapSqlParameterSource p = new MapSqlParameterSource("event", eventId);
        String where = " from scan_logs s left join tickets t on t.id = s.ticket_id left join gates g on g.id = s.gate_id"
                + " join users u on u.id = s.scanned_by where s.event_id = :event";
        if (result != null && !result.isBlank()) {
            String r = result.strip().toUpperCase();
            if (!RESULTS.contains(r)) {
                throw FieldErrors.single("result", "is not a scan result");
            }
            p.addValue("result", r);
            where += " and s.result = :result";
        }
        Pageable pageable = PageRequests.of(page, size, Sort.unsorted());
        Long total = sql.queryForObject("select count(*)" + where, p, Long.class);
        p.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<ScanLogRow> rows = sql.query("""
                select s.at, s.result, t.code, t.holder_name, g.name as gate_name, coalesce(u.full_name, u.email) as scanner,
                       s.manual
                """ + where + " order by s.at desc, s.id limit :limit offset :offset", p,
                (rs, n) -> new ScanLogRow(rs.getObject("at", OffsetDateTime.class), rs.getString("result"),
                        rs.getString("code"), rs.getString("holder_name"), rs.getString("gate_name"), rs.getString("scanner"),
                        rs.getBoolean("manual")));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, pageable.getPageNumber(), pageable.getPageSize(), t,
                (int) ((t + pageable.getPageSize() - 1) / pageable.getPageSize()));
    }
}
