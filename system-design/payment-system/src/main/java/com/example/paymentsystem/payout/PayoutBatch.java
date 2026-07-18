package com.example.paymentsystem.payout;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_batches")
public class PayoutBatch {
    @Id
    @Column(name = "payout_batch_id")
    private UUID payoutBatchId;
    @Column(name = "merchant_id")
    private UUID merchantId;
    @Column(name = "amount")
    private long amount;
    @Column(name = "currency")
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PayoutStatus status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "paid_at")
    private Instant paidAt;

    protected PayoutBatch() {
    }

    public PayoutBatch(UUID merchantId, long amount, String currency) {
        this.payoutBatchId = UUID.randomUUID();
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = PayoutStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public void markPaid() {
        status = PayoutStatus.PAID;
        paidAt = Instant.now();
    }

    public UUID getPayoutBatchId() {
        return payoutBatchId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutStatus getStatus() {
        return status;
    }
}
