package com.stagepass.ticketing.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

/**
 * GST tax invoice for the Stagepass convenience fee (spec 8.4a). Tax wording to be confirmed by the company's
 * accountant before going live.
 */
@Component
public class InvoicePdfRenderer {

    public record InvoiceDoc(String companyName, String companyGstin, String companyState, String companyAddress,
            String number, OffsetDateTime issuedAt, String buyerName, String buyerState, String bookingCode,
            long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise) {
    }

    public byte[] render(InvoiceDoc d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Tax Invoice " + d.number());
        doc.open();

        Font h1 = new Font(Font.HELVETICA, 18, Font.BOLD, TicketPdfRenderer.BRAND);
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD, TicketPdfRenderer.INK);
        Font body = new Font(Font.HELVETICA, 10, Font.NORMAL, TicketPdfRenderer.INK);
        Font muted = new Font(Font.HELVETICA, 9, Font.NORMAL, TicketPdfRenderer.MUTED);

        doc.add(new Paragraph("Tax Invoice", h1));
        doc.add(new Paragraph(PdfText.safe(d.companyName()), bold));
        if (d.companyAddress() != null && !d.companyAddress().isBlank()) {
            doc.add(new Paragraph(PdfText.safe(d.companyAddress()), body));
        }
        doc.add(new Paragraph("GSTIN " + d.companyGstin() + "  ·  State " + PdfText.safe(d.companyState()), body));

        Paragraph meta = new Paragraph();
        meta.setSpacingBefore(14);
        meta.add(new Phrase("Invoice " + d.number() + "\n", bold));
        meta.add(new Phrase("Date " + PdfText.date(d.issuedAt()) + "\n", body));
        meta.add(new Phrase("Billed to " + PdfText.safe(d.buyerName())
                + (d.buyerState() == null ? "" : ", " + PdfText.safe(d.buyerState())) + "\n", body));
        meta.add(new Phrase("Place of supply " + PdfText.safe(d.buyerState() == null ? d.companyState() : d.buyerState()), body));
        doc.add(meta);

        PdfPTable t = new PdfPTable(new float[] {3, 1});
        t.setWidthPercentage(100);
        t.setSpacingBefore(18);
        row(t, "Description", "Amount", bold);
        row(t, "Convenience fee - booking " + d.bookingCode(), PdfText.rupees(d.taxablePaise()), body);
        row(t, "Taxable value", PdfText.rupees(d.taxablePaise()), body);
        if (d.igstPaise() > 0 || (d.cgstPaise() == 0 && d.sgstPaise() == 0)) {
            row(t, "IGST 18%", PdfText.rupees(d.igstPaise()), body);
        } else {
            row(t, "CGST 9%", PdfText.rupees(d.cgstPaise()), body);
            row(t, "SGST 9%", PdfText.rupees(d.sgstPaise()), body);
        }
        row(t, "Total", PdfText.rupees(d.totalPaise()), bold);
        doc.add(t);

        Paragraph note = new Paragraph("Ticket prices are collected on behalf of the event organizer and are not part of "
                + "this invoice.", muted);
        note.setSpacingBefore(18);
        doc.add(note);
        doc.close();
        return out.toByteArray();
    }

    private static void row(PdfPTable t, String left, String right, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(left, font));
        PdfPCell r = new PdfPCell(new Phrase(right, font));
        for (PdfPCell c : new PdfPCell[] {l, r}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(new java.awt.Color(0xE2, 0xE4, 0xEA));
            c.setPadding(6);
        }
        r.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(r);
    }
}
