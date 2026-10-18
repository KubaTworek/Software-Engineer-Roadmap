package com.example.newsfeed.moderation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only ślad decyzji operatora; nie jest zastępowany aktualnym stanem review. */
@Entity
@Table(name = "moderation_audit_log")
public class ModerationAuditLog {
    @Id private UUID id;
    @Column(nullable = false) private UUID reviewId;
    @Column(nullable = false) private UUID entityId;
    @Column(nullable = false) private UUID reviewerId;
    @Column(nullable = false, length = 30) private String decision;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(nullable = false) private Instant decidedAt;
    protected ModerationAuditLog() { }
    public ModerationAuditLog(UUID id, UUID reviewId, UUID entityId, UUID reviewerId,
                              String decision, String reason, Instant decidedAt) {
        this.id = id; this.reviewId = reviewId; this.entityId = entityId; this.reviewerId = reviewerId;
        this.decision = decision; this.reason = reason; this.decidedAt = decidedAt;
    }
}
