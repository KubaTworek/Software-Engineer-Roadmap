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
}
