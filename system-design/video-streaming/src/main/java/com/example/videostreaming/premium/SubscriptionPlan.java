package com.example.videostreaming.premium;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code")
    private SubscriptionPlanCode code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private int level;

    @Column(name = "monthly_price_cents", nullable = false)
    private int monthlyPriceCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubscriptionPlan() {}

    public SubscriptionPlanCode getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public int getMonthlyPriceCents() { return monthlyPriceCents; }
    public Instant getCreatedAt() { return createdAt; }
}
