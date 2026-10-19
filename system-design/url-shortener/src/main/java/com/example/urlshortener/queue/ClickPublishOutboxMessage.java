package com.example.urlshortener.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "click_publish_outbox")
public class ClickPublishOutboxMessage {
    @Id @Column(name = "event_id", length = 36) private String eventId;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "dead_lettered_at") private Instant deadLetteredAt;
    @Column(name = "last_error", length = 512) private String lastError;

    protected ClickPublishOutboxMessage() {}
    public ClickPublishOutboxMessage(String eventId, String payloadJson, Instant createdAt) {
        this.eventId = eventId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }
    public String getEventId() { return eventId; }
    public String getPayloadJson() { return payloadJson; }
    public int getAttempts() { return attempts; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }

    void claim(Instant until) { attempts++; lockedUntil = until; }
    void published(Instant at) { publishedAt = at; lockedUntil = null; lastError = null; }
    void retryAt(Instant at, String error) { nextAttemptAt = at; lockedUntil = null; lastError = truncate(error); }
    void deadLetter(Instant at, String error) { deadLetteredAt = at; lockedUntil = null; lastError = truncate(error); }

    private String truncate(String error) {
        return error == null ? null : error.substring(0, Math.min(512, error.length()));
    }
}
