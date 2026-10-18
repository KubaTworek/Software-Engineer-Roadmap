package com.example.paymentsystem.webhook;

import com.example.paymentsystem.payment.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Fast receiver path: authenticate, persist, acknowledge. Business processing
 * happens after durable storage, so a process crash does not lose the event.
 */
@Service
public class WebhookInboxService {
    private final WebhookSignatureVerifier verifier;
    private final WebhookInboxWriter writer;
    private final WebhookEventRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;

    public WebhookInboxService(WebhookSignatureVerifier verifier, WebhookInboxWriter writer,
                               WebhookEventRepository repository, ObjectMapper objectMapper,
                               MeterRegistry metrics) {
        this.verifier = verifier;
        this.writer = writer;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public WebhookEvent receive(PaymentProvider provider, long timestamp, String signature, String rawBody) {
        verifier.verify(timestamp, rawBody, signature);
        try {
            ProviderWebhookPayload payload = objectMapper.readValue(rawBody, ProviderWebhookPayload.class);
            if (payload.version() != 1) {
                // A valid signature proves origin, not semantic compatibility.
                // Persisting an unknown contract could poison the worker later.
                throw new IllegalArgumentException("Unsupported webhook version: " + payload.version());
            }
            try {
                WebhookEvent saved = writer.insert(new WebhookEvent(provider, payload.eventId(), payload.eventType(),
                        payload.providerPaymentId(), rawBody, payload.occurredAt()));
                metrics.counter("payment.webhook.received", "provider", provider.name()).increment();
                return saved;
            } catch (DataIntegrityViolationException duplicate) {
                metrics.counter("payment.webhook.duplicate", "provider", provider.name()).increment();
                return repository.findByProviderAndProviderEventId(provider, payload.eventId())
                        .orElseThrow(() -> duplicate);
            }
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicate;
        } catch (IllegalArgumentException contractViolation) {
            throw contractViolation;
        } catch (Exception invalidPayload) {
            throw new IllegalArgumentException("Invalid webhook payload", invalidPayload);
        }
    }
}
