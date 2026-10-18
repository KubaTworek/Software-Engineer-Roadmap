package com.example.urlshortener.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "url_projection_outbox")
public class UrlProjectionOutboxMessage {
    @Id @Column(name = "event_id", length = 36) private String eventId;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "dead_lettered_at") private Instant deadLetteredAt;
    @Column(name = "last_error", length = 512) private String lastError;

    protected UrlProjectionOutboxMessage() {}

    public UrlProjectionOutboxMessage(String eventId, String payloadJson, Instant createdAt) {
        this.eventId = eventId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }

    public String getEventId() { return eventId; }
    public String getPayloadJson() { return payloadJson; }
    public int getAttempts() { return attempts; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }

    void claim(Instant lockedUntil) {
        this.attempts++;
        this.lockedUntil = lockedUntil;
    }

    void retryAt(Instant nextAttemptAt, String error) {
        this.nextAttemptAt = nextAttemptAt;
        this.lockedUntil = null;
        this.lastError = truncate(error);
    }

    void deadLetter(Instant now, String error) {
        this.deadLetteredAt = now;
        this.lockedUntil = null;
        this.lastError = truncate(error);
    }

    private String truncate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }
}
