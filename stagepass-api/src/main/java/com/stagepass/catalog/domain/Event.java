package com.stagepass.catalog.domain;

import com.stagepass.identity.domain.Organizer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id")
    private Organizer organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    private String summary;

    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> gallery = new ArrayList<>();

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "online_url")
    private String onlineUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    private String terms;

    @Column(name = "max_tickets_per_user")
    private Integer maxTicketsPerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_policy", nullable = false)
    private RefundPolicy refundPolicy = RefundPolicy.NONE;

    @Column(name = "refund_cutoff_hours")
    private Integer refundCutoffHours;

    @Column(name = "reschedule_refund_until")
    private OffsetDateTime rescheduleRefundUntil;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "convenience_fee_override")
    private Map<String, Object> convenienceFeeOverride;

    @Column(name = "commission_bps_override")
    private Integer commissionBpsOverride;

    @Column(name = "popularity_score", nullable = false)
    private double popularityScore;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, createdAt ASC")
    private List<TicketType> ticketTypes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;
}
