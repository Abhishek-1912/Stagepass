package com.stagepass.catalog.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_types")
@Getter
@Setter
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(name = "sale_starts_at")
    private OffsetDateTime saleStartsAt;

    @Column(name = "sale_ends_at")
    private OffsetDateTime saleEndsAt;

    @Column(name = "min_per_order", nullable = false)
    private int minPerOrder = 1;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder = 10;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketTypeStatus status = TicketTypeStatus.ACTIVE;

    @OneToOne(mappedBy = "ticketType", cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    private TicketInventory inventory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private long version;

    /** A new ticket type with its inventory row, attached to {@code event}. */
    public static TicketType create(Event event, String name, long pricePaise, int quantity) {
        TicketType type = new TicketType();
        type.setEvent(event);
        type.setName(name);
        type.setPricePaise(pricePaise);
        TicketInventory inventory = new TicketInventory();
        inventory.setTicketType(type);
        inventory.setTotal(quantity);
        type.setInventory(inventory);
        return type;
    }
}
