package com.example.notification.domain;

import java.time.Instant;

/** Inbox oddziela trwałe przyjęcie callbacku od jego późniejszego zastosowania. */
public class WebhookInboxEvent {
    private final String eventId;
    private final String providerMessageId;
    private final ProviderWebhookStatus status;
    private final String reason;
    private final Instant receivedAt;
    private Instant processedAt;
    private String lastError;
    private int attempts;
    private Instant nextAttemptAt;
    private boolean dead;

    public WebhookInboxEvent(String eventId, String providerMessageId, ProviderWebhookStatus status, String reason) {
        this(eventId, providerMessageId, status, reason, Instant.now());
    }
    public WebhookInboxEvent(String eventId, String providerMessageId, ProviderWebhookStatus status,
                             String reason, Instant receivedAt) {
        this.eventId = eventId; this.providerMessageId = providerMessageId;
        this.status = status; this.reason = reason; this.receivedAt = receivedAt;
        this.nextAttemptAt = receivedAt;
    }
    public String getEventId() { return eventId; }
    public String getProviderMessageId() { return providerMessageId; }
    public ProviderWebhookStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getLastError() { return lastError; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public boolean isDead() { return dead; }
    public boolean isProcessed() { return processedAt != null; }
    public void markProcessed(Instant now) { processedAt = now; lastError = null; }
    public void markFailed(String error, Instant now, int maxAttempts) {
        lastError = error;
        attempts++;
        dead = attempts >= maxAttempts;
        nextAttemptAt = now.plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    }
    public void restoreProcessing(Instant processedAt, String error, int attempts, Instant nextAttemptAt, boolean dead) {
        this.processedAt = processedAt; this.lastError = error; this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt; this.dead = dead;
    }
}
