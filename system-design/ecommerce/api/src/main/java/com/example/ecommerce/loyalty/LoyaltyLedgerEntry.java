package com.example.ecommerce.loyalty;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "loyalty_ledger")
public class LoyaltyLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private LoyaltyAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoyaltyLedgerType type;

    @Column(nullable = false)
    private int points;

    private Long orderId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected LoyaltyLedgerEntry() {}

    public LoyaltyLedgerEntry(LoyaltyAccount account, LoyaltyLedgerType type, int points, Long orderId, String reason) {
        this.account = account;
        this.type = type;
        this.points = points;
        this.orderId = orderId;
        this.reason = reason;
    }
}
