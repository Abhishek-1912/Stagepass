package com.stagepass.ticketing;

import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.stagepass.booking.BookingTestSupport;
import com.stagepass.booking.BookingTestSupport.LiveEvent;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.ticketing.service.TicketDocuments;
import com.stagepass.ticketing.service.TicketPdfRenderer;
import com.stagepass.ticketing.service.TicketPdfRenderer.TicketPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPdfRendererTest extends PostgresIntegrationTest {

    @Autowired
    TicketDocuments documents;

    @Autowired
    TicketPdfRenderer renderer;

    @Autowired
    BookingTestSupport support;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
        }
        return sb.toString();
    }

    private static int images(PdfReader reader, int page) {
        PdfDictionary xobjects = reader.getPageN(page).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT);
        return xobjects == null ? 0 : xobjects.size();
    }

    private static TicketPage page(String holder, String title, int i) {
        return new TicketPage(title, OffsetDateTime.parse("2026-11-14T13:00:00Z"), "Nirvana Convention, Hyderabad", holder,
                "General", 49900, "SP-AAAA-" + (2000 + i), "SP-ABC234", "VALID", "token-" + i, "No re-entry");
    }

    @Test
    void onePagePerLiveTicket() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.confirmed(e, 3, users.attendee());
        jdbc.update("update tickets set status = 'REFUNDED' where id = ?", b.tickets().get(2).id());

        byte[] pdf = documents.ticketPdf(b.id()).orElseThrow().bytes();
        PdfReader reader = new PdfReader(pdf);
        String text = text(pdf);

        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        assertThat(images(reader, 1)).isEqualTo(1);
        assertThat(images(reader, 2)).isEqualTo(1);
        assertThat(text).contains(b.code(), b.tickets().get(0).code(), b.tickets().get(1).code(), "Asha Rao", "Rs. 499")
                .doesNotContain(b.tickets().get(2).code());
        assertThat(documents.ticketPdf(b.id()).orElseThrow().filename()).isEqualTo("stagepass-" + b.code() + ".pdf");
    }

    @Test
    void twentyTicketsTwentyPages() throws Exception {
        List<TicketPage> pages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pages.add(page("Asha Rao", "Sunburn Hyderabad", i));
        }
        assertThat(new PdfReader(renderer.render(pages)).getNumberOfPages()).isEqualTo(20);
    }

    @Test
    void nonLatinTextDoesNotBreakRendering() throws Exception {
        byte[] pdf = renderer.render(List.of(page("Asha राव", "Diwali 🎆 Night", 1)));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains("Asha", "Diwali", "Night");
    }

    @Test
    void invoiceSplitsTaxes() throws Exception {
        LiveEvent e = support.liveEvent(49900, 10);
        BookingResponse b = support.confirmed(e, 2, users.attendee()); // buyer state Telangana = company state

        var invoice = documents.invoicePdf(b.id()).orElseThrow();
        String text = text(invoice.bytes());

        assertThat(text).contains("Tax Invoice", "36ABCDE1234F1Z5", "SP/2026-27/", "CGST", "Rs. 4.05", "SGST", "Rs. 4.04",
                "Rs. 44.95", "Rs. 53.04", b.code());
        assertThat(invoice.filename()).startsWith("stagepass-invoice-SP-2026-27-").endsWith(".pdf");
    }

    @Test
    void noLiveTicketsNoPdf() {
        LiveEvent e = support.liveEvent(0, 10);
        BookingResponse b = support.confirmed(e, 1, users.attendee());
        jdbc.update("update tickets set status = 'REFUNDED' where booking_id = ?", b.id());
        assertThat(documents.ticketPdf(b.id())).isEmpty();
        assertThat(documents.invoicePdf(b.id())).isEmpty();
    }

    @Test
    void longTitleAndTermsStayOnOnePagePerTicket() throws Exception {
        String title = "The Grand Hyderabad Winter Music, Food and Arts Festival ".repeat(3).substring(0, 140);
        String terms = ("No re-entry.\nCarry a valid photo ID.\nOutside food not allowed. ").repeat(12).substring(0, 600);
        List<TicketPage> pages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pages.add(new TicketPage(title, OffsetDateTime.parse("2026-11-14T13:00:00Z"), "Nirvana Convention Centre, Hyderabad",
                    "Asha Rao", "General Admission", 49900, "SP-AAAA-" + (2000 + i), "SP-ABC234", "VALID", "token-" + i, terms));
        }
        assertThat(new PdfReader(renderer.render(pages)).getNumberOfPages()).isEqualTo(20);
    }

    @Test
    void nameOnlyInAnotherScriptFallsBack() throws Exception {
        byte[] pdf = renderer.render(List.of(page("राव", "దీపావళి", 1)));
        assertThat(text(pdf)).contains("Ticket holder").contains("SP-AAAA-2001");
    }
}
