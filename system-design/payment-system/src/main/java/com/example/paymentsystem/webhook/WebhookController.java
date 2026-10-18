package com.example.paymentsystem.webhook;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {
    private final WebhookInboxService inbox;

    public WebhookController(WebhookInboxService inbox) { this.inbox = inbox; }

    @PostMapping("/{provider}")
    ResponseEntity<Map<String, Object>> receive(@PathVariable PaymentProvider provider,
                                                 @RequestHeader("X-Webhook-Timestamp") long timestamp,
                                                 @RequestHeader("X-Webhook-Signature") String signature,
                                                 @RequestBody String rawBody) {
        WebhookEvent event = inbox.receive(provider, timestamp, signature, rawBody);
        return ResponseEntity.accepted().body(Map.of("eventId", event.getWebhookEventId(), "status", event.getStatus()));
    }
}
