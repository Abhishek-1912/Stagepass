package com.stagepass.payment.api;

import com.stagepass.booking.api.BookingResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentVerifyController {

    private final PaymentService payments;
    private final CurrentUser currentUser;

    public PaymentVerifyController(PaymentService payments, CurrentUser currentUser) {
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/payments/verify")
    public BookingResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
        return payments.verify(currentUser.id(), request);
    }
}
