package com.stagepass.admin.service;

import com.stagepass.admin.api.AdminRefundRow;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.booking.service.RefundService;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin refunds of a booking and retries of refunds the gateway refused. Not transactional: RefundService owns that. */
@Service
public class AdminRefundService {

    private final RefundService refunds;
    private final AdminTransactionQueries queries;
    private final AuditLogService audit;

    public AdminRefundService(RefundService refunds, AdminTransactionQueries queries, AuditLogService audit) {
        this.refunds = refunds;
        this.queries = queries;
        this.audit = audit;
    }

    public BookingResponse refund(UUID adminId, UUID bookingId, List<UUID> ticketIds, boolean includeFee, String reason) {
        return refunds.adminRefund(adminId, bookingId, ticketIds, includeFee, reason.strip());
    }

    /** Only a FAILED refund queued for retry can be sent again. */
    public AdminRefundRow retry(UUID adminId, UUID refundId) {
        AdminRefundRow row = queries.refund(refundId);
        if (row == null) {
            throw ApiException.notFound("Refund");
        }
        if (!"FAILED".equals(row.status()) || !row.needsRetry()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only failed refunds waiting for a retry can be retried.");
        }
        refunds.retry(refundId);
        audit.record(adminId, "REFUND_RETRIED", "refund", refundId.toString(), Map.of("status", "FAILED"), null);
        return queries.refund(refundId);
    }
}
