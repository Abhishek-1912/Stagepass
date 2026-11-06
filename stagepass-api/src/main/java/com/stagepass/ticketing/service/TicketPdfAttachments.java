package com.stagepass.ticketing.service;

import com.stagepass.notification.service.EmailAttachment;
import com.stagepass.notification.service.EmailAttachmentProvider;
import com.stagepass.notification.service.EmailTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attaches the ticket PDF to the booking-confirmed email, built at send time from current ticket state. */
@Component
public class TicketPdfAttachments implements EmailAttachmentProvider {

    private final TicketDocuments documents;

    public TicketPdfAttachments(TicketDocuments documents) {
        this.documents = documents;
    }

    @Override
    public boolean supports(EmailTemplate template) {
        return template == EmailTemplate.BOOKING_CONFIRMED;
    }

    @Override
    public List<EmailAttachment> attachmentsFor(EmailTemplate template, Map<String, ?> data) {
        if (!(data.get("bookingId") instanceof String id)) {
            return List.of();
        }
        UUID bookingId;
        try {
            bookingId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return documents.ticketPdf(bookingId).map(pdf -> List.of(new EmailAttachment(pdf.filename(), pdf.bytes())))
                .orElse(List.of());
    }
}
