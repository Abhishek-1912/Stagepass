package com.stagepass.checkin.service;

import com.stagepass.checkin.api.GateRequest;
import com.stagepass.checkin.api.GateResponse;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Named gates of an organizer's event, each optionally limited to some ticket types. History survives deletion. */
@Service
public class GateService {

    private final NamedParameterJdbcTemplate sql;
    private final AuditLogService audit;

    public GateService(NamedParameterJdbcTemplate sql, AuditLogService audit) {
        this.sql = sql;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GateResponse> list(UUID organizerId, UUID eventId) {
        requireOwnedEvent(organizerId, eventId);
        return list0(eventId);
    }

    @Transactional
    public GateResponse create(UUID organizerId, UUID actorId, UUID eventId, GateRequest r) {
        requireOwnedEvent(organizerId, eventId);
        validate(eventId, null, r);
        UUID id = sql.queryForObject("""
                insert into gates (event_id, name, sort_order)
                values (:event, :name, (select coalesce(max(sort_order), 0) + 1 from gates where event_id = :event))
                returning id
                """, new MapSqlParameterSource("event", eventId).addValue("name", r.name().strip()), UUID.class);
        setTypes(id, r.typesOrEmpty());
        audit.record(actorId, "GATE_CREATED", "gate", id.toString(), null, Map.of("name", r.name().strip(), "eventId", eventId.toString()));
        return find(eventId, id);
    }

    @Transactional
    public GateResponse update(UUID organizerId, UUID actorId, UUID eventId, UUID gateId, GateRequest r) {
        requireOwnedEvent(organizerId, eventId);
        requireGate(eventId, gateId);
        validate(eventId, gateId, r);
        sql.update("update gates set name = :name where id = :gate",
                new MapSqlParameterSource("gate", gateId).addValue("name", r.name().strip()));
        setTypes(gateId, r.typesOrEmpty());
        audit.record(actorId, "GATE_UPDATED", "gate", gateId.toString(), null, Map.of("name", r.name().strip()));
        return find(eventId, gateId);
    }

    @Transactional
    public void delete(UUID organizerId, UUID actorId, UUID eventId, UUID gateId) {
        requireOwnedEvent(organizerId, eventId);
        requireGate(eventId, gateId);
        sql.update("delete from gates where id = :gate", new MapSqlParameterSource("gate", gateId));
        audit.record(actorId, "GATE_DELETED", "gate", gateId.toString(), null, Map.of("eventId", eventId.toString()));
    }

    private void validate(UUID eventId, UUID selfId, GateRequest r) {
        FieldErrors errors = FieldErrors.start();
        Integer clash = sql.queryForObject("""
                select count(*) from gates where event_id = :event and lower(name) = lower(:name)
                  and (cast(:self as uuid) is null or id <> :self)
                """, new MapSqlParameterSource("event", eventId).addValue("name", r.name().strip()).addValue("self", selfId),
                Integer.class);
        errors.addIf(clash != null && clash > 0, "name", "another gate already has this name");
        List<UUID> types = r.typesOrEmpty();
        if (!types.isEmpty()) {
            Integer own = sql.queryForObject("select count(*) from ticket_types where event_id = :event and id in (:ids)",
                    new MapSqlParameterSource("event", eventId).addValue("ids", types), Integer.class);
            errors.addIf(own == null || own != types.size(), "ticketTypeIds", "must be ticket types of this event");
        }
        errors.throwIfAny();
    }

    private void setTypes(UUID gateId, List<UUID> types) {
        sql.update("delete from gate_ticket_types where gate_id = :gate", new MapSqlParameterSource("gate", gateId));
        for (UUID t : types) {
            sql.update("insert into gate_ticket_types (gate_id, ticket_type_id) values (:gate, :type)",
                    new MapSqlParameterSource("gate", gateId).addValue("type", t));
        }
    }

    private GateResponse find(UUID eventId, UUID gateId) {
        return list0(eventId).stream().filter(g -> g.id().equals(gateId)).findFirst().orElseThrow();
    }

    private List<GateResponse> list0(UUID eventId) {
        return sql.query("""
                select g.id, g.name,
                       coalesce((select array_agg(x.ticket_type_id) from gate_ticket_types x where x.gate_id = g.id),
                                '{}'::uuid[]) as types,
                       (select count(*) from check_ins c where c.gate_id = g.id) as checked_in
                from gates g where g.event_id = :event
                order by g.sort_order, g.name
                """, new MapSqlParameterSource("event", eventId), (rs, n) -> new GateResponse(rs.getObject("id", UUID.class),
                rs.getString("name"), uuids(rs.getArray("types")), rs.getInt("checked_in")));
    }

    private void requireOwnedEvent(UUID organizerId, UUID eventId) {
        Integer n = sql.queryForObject("select count(*) from events where id = :event and organizer_id = :org",
                new MapSqlParameterSource("event", eventId).addValue("org", organizerId), Integer.class);
        if (n == null || n == 0) {
            throw ApiException.notFound("Event");
        }
    }

    private void requireGate(UUID eventId, UUID gateId) {
        Integer n = sql.queryForObject("select count(*) from gates where id = :gate and event_id = :event",
                new MapSqlParameterSource("gate", gateId).addValue("event", eventId), Integer.class);
        if (n == null || n == 0) {
            throw ApiException.notFound("Gate");
        }
    }

    static List<UUID> uuids(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(v -> v instanceof UUID u ? u : UUID.fromString(v.toString())).toList();
    }
}
