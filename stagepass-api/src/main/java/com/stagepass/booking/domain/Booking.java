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

/** A purchase of one or more tickets for one event by one user. */
@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code")
    private String code;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(name = "subtotal_paise")
    private long subtotalPaise;

    @Column(name = "fee_paise")
    private long feePaise;

    @Column(name = "fee_gst_paise")
    private long feeGstPaise;

    @Column(name = "total_paise")
    private long totalPaise;

    @Column(name = "commission_paise")
    private long commissionPaise;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "attendee_name")
    private String attendeeName;

    @Column(name = "attendee_email")
    private String attendeeEmail;

    @Column(name = "attendee_phone")
    private String attendeePhone;

    @Column(name = "buyer_state")
    private String buyerState;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, code ASC")
    private List<Ticket> tickets = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;
}
