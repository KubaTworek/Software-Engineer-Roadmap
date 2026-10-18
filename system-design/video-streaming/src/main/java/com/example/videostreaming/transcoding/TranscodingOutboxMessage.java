package com.example.videostreaming.transcoding;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Trwała intencja publikacji komendy transkodowania. */
@Entity
@Table(name = "transcoding_outbox")
public class TranscodingOutboxMessage {
    @Id private UUID id;
    @Column(name = "job_id", nullable = false, unique = true) private UUID jobId;
    @Column(name = "video_id", nullable = false) private UUID videoId;
    @Column(name = "source_object_key", nullable = false) private String sourceObjectKey;
    @Column(nullable = false) private int attempt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "delivery_attempts", nullable = false) private int deliveryAttempts;
    @Column(name = "last_error") private String lastError;

    protected TranscodingOutboxMessage() {}

    public TranscodingOutboxMessage(UUID jobId, UUID videoId, String sourceObjectKey, int attempt, Instant now) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.videoId = videoId;
        this.sourceObjectKey = sourceObjectKey;
        this.attempt = attempt;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public UUID getVideoId() { return videoId; }
    public String getSourceObjectKey() { return sourceObjectKey; }
    public int getAttempt() { return attempt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getDeliveryAttempts() { return deliveryAttempts; }

    public void published(Instant now) {
        this.publishedAt = now;
        this.deliveryAttempts++;
        this.lastError = null;
    }

    public void failed(String error) {
        this.deliveryAttempts++;
        this.lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1_000));
    }
}
