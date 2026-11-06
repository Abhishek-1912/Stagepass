package com.stagepass.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An organizer profile (one per user). Becomes usable for selling once {@link OrganizerStatus#APPROVED}. */
@Entity
@Table(name = "organizers")
@Getter
@Setter
public class Organizer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "org_name", nullable = false)
    private String orgName;

    @Column(nullable = false, unique = true)
    private String slug;

    private String bio;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String website;

    @Column(nullable = false)
    private String pan;

    private String gstin;

    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizerStatus status = OrganizerStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "payout_account_name")
    private String payoutAccountName;

    @Column(name = "payout_account_number_enc")
    private String payoutAccountNumberEnc;

    @Column(name = "payout_ifsc")
    private String payoutIfsc;

    @Column(name = "commission_bps_override")
    private Integer commissionBpsOverride;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt = OffsetDateTime.now();

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;
}
