package com.example.ecommerce.loyalty;

import com.example.ecommerce.auth.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "loyalty_accounts", indexes = {
        @Index(name = "idx_loyalty_user", columnList = "user_id", unique = true)
})
public class LoyaltyAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private int pointsBalance = 0;

    @Column(nullable = false)
    private String tier = "BRONZE";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected LoyaltyAccount() {}

    public LoyaltyAccount(AppUser user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public int getPointsBalance() { return pointsBalance; }
    public String getTier() { return tier; }

    public void addPoints(int points) {
        this.pointsBalance += Math.max(0, points);
        recalculateTier();
    }

    public void redeemPoints(int points) {
        if (points > pointsBalance) {
            throw new IllegalArgumentException("Not enough loyalty points");
        }
        this.pointsBalance -= points;
        recalculateTier();
    }

    private void recalculateTier() {
        if (pointsBalance >= 10000) {
            tier = "PLATINUM";
        } else if (pointsBalance >= 5000) {
            tier = "GOLD";
        } else if (pointsBalance >= 1000) {
            tier = "SILVER";
        } else {
            tier = "BRONZE";
        }
    }
}
