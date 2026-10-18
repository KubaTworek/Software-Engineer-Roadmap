package com.example.paymentsystem.webhook;

import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Durable inbox entry. The unique provider event id is the deduplication boundary. */
@Entity
@Table(name = "webhook_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_webhook_provider_event", columnNames = {"provider", "provider_event_id"}))
public class WebhookEvent {
    @Id @Column(name = "webhook_event_id") private UUID webhookEventId;
    @Enumerated(EnumType.STRING) @Column(name = "provider") private PaymentProvider provider;
    @Column(name = "provider_event_id") private String providerEventId;
    @Column(name = "event_type") private String eventType;
    @Column(name = "provider_payment_id") private String providerPaymentId;
    @Column(name = "payload", columnDefinition = "TEXT") private String payload;
    @Enumerated(EnumType.STRING) @Column(name = "status") private WebhookStatus status;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "occurred_at") private Instant occurredAt;
    @Column(name = "received_at") private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;

    protected WebhookEvent() { }

    public WebhookEvent(PaymentProvider provider, String providerEventId, String eventType,
                        String providerPaymentId, String payload, Instant occurredAt) {
        this.webhookEventId = UUID.randomUUID();
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.providerPaymentId = providerPaymentId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.receivedAt = Instant.now();
        this.status = WebhookStatus.RECEIVED;
    }

    public void processed() { status = WebhookStatus.PROCESSED; processedAt = Instant.now(); }
    public void failed(Throwable error) { status = WebhookStatus.FAILED; failureReason = error.getMessage(); }
    public UUID getWebhookEventId() { return webhookEventId; }
    public PaymentProvider getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public WebhookStatus getStatus() { return status; }
}
