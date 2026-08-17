package com.example.paymentsystem.merchant;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "settlement_currency", nullable = false)
    private String settlementCurrency;

    @Column(name = "payout_enabled", nullable = false)
    private boolean payoutEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Merchant() {
    }

    public Merchant(String name, String settlementCurrency) {
        this.merchantId = UUID.randomUUID();
        this.name = name;
        this.settlementCurrency = settlementCurrency;
        this.payoutEnabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getName() {
        return name;
    }

    public String getSettlementCurrency() {
        return settlementCurrency;
    }

    public boolean isPayoutEnabled() {
        return payoutEnabled;
    }
}
