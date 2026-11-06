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

/** Quantity of one ticket type within a booking, at the price paid. */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "unit_price_paise")
    private long unitPricePaise;
}
