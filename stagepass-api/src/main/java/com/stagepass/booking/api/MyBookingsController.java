package com.stagepass.booking.api;

import com.stagepass.booking.service.BookingService;
import com.stagepass.booking.service.RefundService;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/bookings")
public class MyBookingsController {

    private final BookingService bookings;
    private final RefundService refunds;
    private final CurrentUser currentUser;

    public MyBookingsController(BookingService bookings, RefundService refunds, CurrentUser currentUser) {
        this.bookings = bookings;
        this.refunds = refunds;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<BookingSummary> list(@RequestParam(required = false) String scope,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return bookings.list(currentUser.id(), scope, page, size);
    }

    @GetMapping("/{id}")
    public BookingResponse detail(@PathVariable UUID id) {
        return bookings.detail(currentUser.id(), id);
    }

    @PostMapping("/{id}/refunds")
    public BookingResponse requestRefund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return refunds.request(currentUser.id(), id, request);
    }
}
