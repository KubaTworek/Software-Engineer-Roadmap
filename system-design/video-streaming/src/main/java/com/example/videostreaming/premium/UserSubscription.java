package com.example.videostreaming.premium;

import com.example.videostreaming.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false)
    private SubscriptionPlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSubscription() {}

    public UserSubscription(User user, SubscriptionPlanCode planCode, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.planCode = planCode;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public SubscriptionPlanCode getPlanCode() { return planCode; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isActiveAt(Instant now) {
        return status == SubscriptionStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.updatedAt = this.cancelledAt;
    }
}
