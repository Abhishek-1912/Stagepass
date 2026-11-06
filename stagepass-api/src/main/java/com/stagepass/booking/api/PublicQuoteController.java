package com.stagepass.booking.api;

import com.stagepass.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Exact price for a ticket selection, before signing in. Nothing is held. */
@RestController
public class PublicQuoteController {

    private final BookingService bookings;

    public PublicQuoteController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/api/v1/public/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return bookings.quote(request);
    }
}
