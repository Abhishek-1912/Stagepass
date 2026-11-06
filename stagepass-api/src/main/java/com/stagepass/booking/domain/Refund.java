package com.stagepass.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A refund of some or all tickets of a booking. */
@Entity
@Table(name = "refunds")
@Getter
@Setter
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "razorpay_refund_id")
    private String razorpayRefundId;

    @Column(name = "amount_paise")
    private long amountPaise;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status = RefundStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private RefundKind kind;

    @Column(name = "fee_paise")
    private long feePaise;

    @Column(name = "fee_gst_paise")
    private long feeGstPaise;

    @Column(name = "commission_paise")
    private long commissionPaise;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ticket_ids", nullable = false)
    private UUID[] ticketIds = new UUID[0];

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "needs_retry", nullable = false)
    private boolean needsRetry;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;
}
