package com.stagepass.booking.api;

import com.stagepass.booking.service.BookingService;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookings;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookings, CurrentUser currentUser) {
        this.bookings = bookings;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingService.Result result = bookings.create(currentUser.id(), idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.booking());
    }
}
