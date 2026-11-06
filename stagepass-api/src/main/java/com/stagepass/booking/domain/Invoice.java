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

/** GST tax invoice for the convenience fee of a paid booking. */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "number")
    private String number;

    @Column(name = "financial_year")
    private String financialYear;

    @Column(name = "seq")
    private int seq;

    @Column(name = "taxable_paise")
    private long taxablePaise;

    @Column(name = "cgst_paise")
    private long cgstPaise;

    @Column(name = "sgst_paise")
    private long sgstPaise;

    @Column(name = "igst_paise")
    private long igstPaise;

    @Column(name = "total_paise")
    private long totalPaise;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;
}
