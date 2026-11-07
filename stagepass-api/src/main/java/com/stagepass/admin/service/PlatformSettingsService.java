package com.stagepass.admin.service;

import com.stagepass.admin.api.FeeSettingsRequest;
import com.stagepass.booking.service.FeeSettings;
import com.stagepass.booking.service.FeeSettingsRepository;
import com.stagepass.common.audit.AuditLogService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Platform fee settings (spec 8.2). New values apply to the next quote; stored bookings never change. */
@Service
public class PlatformSettingsService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NamedParameterJdbcTemplate sql;
    private final FeeSettingsRepository fees;
    private final AuditLogService audit;

    public PlatformSettingsService(NamedParameterJdbcTemplate sql, FeeSettingsRepository fees, AuditLogService audit) {
        this.sql = sql;
        this.fees = fees;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public FeeSettings fees() {
        return fees.current();
    }

    @Transactional
    public FeeSettings updateFees(UUID adminId, FeeSettingsRequest r) {
        fees.evict();
        FeeSettings before = fees.current();
        FeeSettings after = new FeeSettings(r.convenienceFeeBps(), r.convenienceFeeFlatPaise(), r.gstBps(),
                r.commissionBps(), r.refundFeeOnAttendeeRequest());
        sql.update("""
                update platform_settings set value = cast(:value as jsonb), updated_by = :admin where key = 'fees'
                """, new MapSqlParameterSource("value", JSON.writeValueAsString(map(after))).addValue("admin", adminId));
        audit.record(adminId, "SETTINGS_UPDATED", "platform_settings", "fees", map(before), map(after));
        fees.evict();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fees.evict();
                }
            });
        }
        return after;
    }

    private static Map<String, Object> map(FeeSettings f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("convenienceFeeBps", f.convenienceFeeBps());
        m.put("convenienceFeeFlatPaise", f.convenienceFeeFlatPaise());
        m.put("gstBps", f.gstBps());
        m.put("commissionBps", f.commissionBps());
        m.put("refundFeeOnAttendeeRequest", f.refundFeeOnAttendeeRequest());
        return m;
    }
}
