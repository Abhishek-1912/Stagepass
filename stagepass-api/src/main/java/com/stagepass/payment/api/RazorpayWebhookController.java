package com.stagepass.payment.api;

import com.stagepass.payment.service.WebhookService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Razorpay → Stagepass. Public; authenticity comes from the HMAC signature over the raw body. */
@RestController
public class RazorpayWebhookController {

    private final WebhookService webhooks;

    public RazorpayWebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/api/v1/webhooks/razorpay")
    public Map<String, String> receive(@RequestBody byte[] body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        webhooks.handle(body, signature, eventId);
        return Map.of("status", "ok");
    }
}
