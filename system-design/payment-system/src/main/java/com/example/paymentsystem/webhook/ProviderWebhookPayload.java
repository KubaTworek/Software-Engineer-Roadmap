package com.example.paymentsystem.webhook;

import java.time.Instant;
import java.util.UUID;

public record ProviderWebhookPayload(
        String eventId,
        String eventType,
        UUID paymentId,
        String providerPaymentId,
        Instant occurredAt,
        int version
) {
    public ProviderWebhookPayload(String eventId, String eventType, UUID paymentId,
                                  String providerPaymentId, Instant occurredAt) {
        this(eventId, eventType, paymentId, providerPaymentId, occurredAt, 1);
    }
}
