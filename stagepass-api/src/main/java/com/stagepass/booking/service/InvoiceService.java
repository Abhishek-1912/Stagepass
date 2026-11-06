package com.stagepass.booking.service;

import com.stagepass.booking.domain.Booking;
import com.stagepass.booking.domain.Invoice;
import com.stagepass.booking.repository.InvoiceRepository;
import com.stagepass.common.config.CompanyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GST tax invoice for the Stagepass convenience fee (the ticket itself is sold on the organizer's behalf).
 * Issued only when {@code stagepass.company.gstin} is set. Tax specifics to be confirmed by the company's accountant.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final InvoiceRepository invoices;
    private final JdbcTemplate jdbc;
    private final CompanyProperties company;
    private final AtomicBoolean warned = new AtomicBoolean();

    public InvoiceService(InvoiceRepository invoices, JdbcTemplate jdbc, CompanyProperties company) {
        this.invoices = invoices;
        this.jdbc = jdbc;
        this.company = company;
    }

    /** Issues (or returns the existing) invoice for a confirmed paid booking. Call inside a transaction. */
    public Optional<Invoice> issueFor(Booking b) {
        if (!company.gstRegistered()) {
            if (warned.compareAndSet(false, true)) {
                log.info("stagepass.company.gstin is not set: no GST invoices will be issued");
            }
            return Optional.empty();
        }
        if (b.getFeePaise() == 0 && b.getFeeGstPaise() == 0) {
            return Optional.empty();
        }
        Optional<Invoice> existing = invoices.findByBookingId(b.getId());
        if (existing.isPresent()) {
            return existing;
        }
        OffsetDateTime issuedAt = b.getConfirmedAt() != null ? b.getConfirmedAt() : OffsetDateTime.now();
        String fy = financialYear(issuedAt);
        Integer seq = jdbc.queryForObject("""
                insert into invoice_sequences (financial_year, last_seq) values (?, 1)
                on conflict (financial_year) do update set last_seq = invoice_sequences.last_seq + 1
                returning last_seq
                """, Integer.class, fy);

        long gst = b.getFeeGstPaise();
        Invoice inv = new Invoice();
        inv.setBookingId(b.getId());
        inv.setFinancialYear(fy);
        inv.setSeq(seq);
        inv.setNumber("SP/%s/%06d".formatted(fy, seq));
        inv.setTaxablePaise(b.getFeePaise());
        if (sameState(b.getBuyerState())) {
            inv.setCgstPaise(gst - gst / 2);
            inv.setSgstPaise(gst / 2);
        } else {
            inv.setIgstPaise(gst);
        }
        inv.setTotalPaise(b.getFeePaise() + gst);
        inv.setIssuedAt(issuedAt);
        return Optional.of(invoices.save(inv));
    }

    /** Indian financial year (April to March, IST), e.g. {@code 2026-27}. */
    static String financialYear(OffsetDateTime at) {
        LocalDate d = at.atZoneSameInstant(IST).toLocalDate();
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return "%d-%02d".formatted(start, (start + 1) % 100);
    }

    private boolean sameState(String buyerState) {
        return buyerState != null && company.state() != null
                && buyerState.strip().equalsIgnoreCase(company.state().strip());
    }
}
