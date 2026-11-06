package com.stagepass.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Sold/held counters for a ticket type. Counters are changed only by conditional SQL updates (see the
 * repositories) so concurrent bookings cannot oversell; this entity is read-mostly.
 */
@Entity
@Table(name = "ticket_inventory")
@Getter
@Setter
public class TicketInventory {

    @Id
    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int sold;

    @Column(nullable = false)
    private int held;

    public int available() {
        return Math.max(0, total - sold - held);
    }
}
