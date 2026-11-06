package com.stagepass.ticketing.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;

/** One A6 page per ticket: event, time, venue, holder, type, codes, QR and terms (spec 8.5). */
@Component
public class TicketPdfRenderer {

    /** Everything printed on one ticket page. */
    public record TicketPage(String eventTitle, OffsetDateTime startsAt, String venue, String holder, String ticketType,
            long pricePaise, String ticketCode, String bookingCode, String status, String qrToken, String terms) {
    }

    static final Color BRAND = new Color(0x6C, 0x4C, 0xF1);
    static final Color INK = new Color(0x17, 0x19, 0x23);
    static final Color MUTED = new Color(0x5A, 0x60, 0x72);
    private static final float MM = 72f / 25.4f;

    private final QrImages qr;

    public TicketPdfRenderer(QrImages qr) {
        this.qr = qr;
    }

    public byte[] render(List<TicketPage> pages) {
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No tickets to render");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A6, 8 * MM, 8 * MM, 8 * MM, 8 * MM);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Stagepass tickets");
        doc.open();
        boolean first = true;
        for (TicketPage p : pages) {
            if (!first) {
                doc.newPage();
            }
            first = false;
            page(doc, p);
        }
        doc.close();
        return out.toByteArray();
    }

    private void page(Document doc, TicketPage p) {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase("STAGEPASS", new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(BRAND);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(6);
        band.addCell(cell);
        doc.add(band);

        doc.add(para(PdfText.safe(p.eventTitle()), new Font(Font.HELVETICA, 13, Font.BOLD, INK), 6));
        doc.add(para(PdfText.dateTime(p.startsAt()), new Font(Font.HELVETICA, 8, Font.NORMAL, INK), 2));
        doc.add(para(PdfText.safe(p.venue()), new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED), 1));

        Font label = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
        Font value = new Font(Font.HELVETICA, 9, Font.BOLD, INK);
        Paragraph details = new Paragraph();
        details.setSpacingBefore(6);
        details.add(new Chunk("Holder  ", label));
        details.add(new Chunk(PdfText.safe(p.holder()), value));
        details.add(Chunk.NEWLINE);
        details.add(new Chunk("Ticket  ", label));
        details.add(new Chunk(PdfText.safe(p.ticketType()) + "  ·  " + PdfText.money(p.pricePaise()), value));
        doc.add(details);

        try {
            Image image = Image.getInstance(qr.png(p.qrToken(), 600));
            image.scaleAbsolute(50 * MM, 50 * MM);
            image.setAlignment(Element.ALIGN_CENTER);
            image.setSpacingBefore(4);
            doc.add(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Paragraph codes = para(p.ticketCode(), new Font(Font.COURIER, 11, Font.BOLD, INK), 0);
        codes.setAlignment(Element.ALIGN_CENTER);
        doc.add(codes);
        String booking = "Booking " + p.bookingCode() + ("CHECKED_IN".equals(p.status()) ? "  ·  Checked in" : "");
        Paragraph bookingLine = para(booking, new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED), 1);
        bookingLine.setAlignment(Element.ALIGN_CENTER);
        doc.add(bookingLine);

        if (p.terms() != null && !p.terms().isBlank()) {
            String terms = PdfText.safe(p.terms());
            doc.add(para(terms.length() > 600 ? terms.substring(0, 600) + "…" : terms,
                    new Font(Font.HELVETICA, 5.5f, Font.NORMAL, MUTED), 5));
        }
    }

    private static Paragraph para(String text, Font font, float spacingBefore) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(spacingBefore);
        return p;
    }
}
