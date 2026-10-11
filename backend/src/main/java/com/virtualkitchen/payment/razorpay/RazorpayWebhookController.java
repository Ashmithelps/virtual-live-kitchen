package com.virtualkitchen.payment.razorpay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configure in Razorpay Dashboard → Webhooks: URL {public base}/api/payments/razorpay/webhook,
 * events payment.captured, payment.failed, refund.processed, secret = RAZORPAY_WEBHOOK_SECRET.
 * Public endpoint; authenticity comes from the HMAC signature over the raw body.
 */
@RestController
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayWebhookController {

    private final RazorpayWebhookService service;

    public RazorpayWebhookController(RazorpayWebhookService service) {
        this.service = service;
    }

    /** The body is taken as the exact raw string: the signature is computed over those bytes. */
    @PostMapping("/api/payments/razorpay/webhook")
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                        @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        service.handle(rawBody, signature, eventId);
        // 200 for applied, duplicate and ignored events alike: anything else makes Razorpay retry.
        return ResponseEntity.ok().build();
    }
}
