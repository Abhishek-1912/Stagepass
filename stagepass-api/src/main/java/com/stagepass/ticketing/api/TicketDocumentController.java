package com.stagepass.ticketing.api;

import com.stagepass.booking.repository.BookingRepository;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.ticketing.service.TicketDocuments;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Ticket and invoice PDFs for the booking owner. */
@RestController
@RequestMapping("/api/v1/me/bookings/{id}")
public class TicketDocumentController {

    private final TicketDocuments documents;
    private final BookingRepository bookings;
    private final CurrentUser currentUser;

    public TicketDocumentController(TicketDocuments documents, BookingRepository bookings, CurrentUser currentUser) {
        this.documents = documents;
        this.bookings = bookings;
        this.currentUser = currentUser;
    }

    @GetMapping("/tickets.pdf")
    public ResponseEntity<byte[]> tickets(@PathVariable UUID id) {
        requireOwner(id);
        return documents.ticketPdf(id).map(TicketDocumentController::pdf).orElseThrow(
                () -> new ApiException(ErrorCode.INVALID_STATE, "This booking has no valid tickets to download."));
    }

    @GetMapping("/invoice.pdf")
    public ResponseEntity<byte[]> invoice(@PathVariable UUID id) {
        requireOwner(id);
        return documents.invoicePdf(id).map(TicketDocumentController::pdf).orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    private void requireOwner(UUID bookingId) {
        bookings.findByIdAndUserId(bookingId, currentUser.id()).orElseThrow(() -> ApiException.notFound("Booking"));
    }

    private static ResponseEntity<byte[]> pdf(TicketDocuments.Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.bytes());
    }
}
