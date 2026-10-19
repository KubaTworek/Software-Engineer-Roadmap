package com.example.newsfeed.moderation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_reviews")
public class ModerationReview {
    @Id private UUID id;
    private String entityType;
    private UUID entityId;
    private String status;
    private double automatedScore;
    @Column(columnDefinition = "TEXT") private String reason;
    private UUID reviewerId;
    private Instant createdAt;
    private Instant reviewedAt;

    protected ModerationReview() {}
    public ModerationReview(UUID id, String entityType, UUID entityId, String status, double automatedScore, String reason, UUID reviewerId, Instant createdAt, Instant reviewedAt) {
        this.id = id; this.entityType = entityType; this.entityId = entityId; this.status = status; this.automatedScore = automatedScore; this.reason = reason; this.reviewerId = reviewerId; this.createdAt = createdAt; this.reviewedAt = reviewedAt;
    }
    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public UUID getEntityId() { return entityId; }
    public String getEntityType() { return entityType; }
    public double getAutomatedScore() { return automatedScore; }
    public String getReason() { return reason; }
    public UUID getReviewerId() { return reviewerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    /** Decyzja jest jednokrotna; ponowne kliknięcie nie może nadpisać audytu. */
    public void decide(String decision, UUID reviewerId, String reason, Instant now) {
        if (!"needs_review".equals(status)) {
            throw new IllegalStateException("Moderation review has already been decided.");
        }
        this.status = decision;
        this.reviewerId = reviewerId;
        this.reviewedAt = now;
        if (reason != null && !reason.isBlank()) this.reason = reason.trim();
    }
}
