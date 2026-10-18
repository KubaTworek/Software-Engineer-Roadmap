package com.example.paymentsystem.payout;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_batches", uniqueConstraints =
        @UniqueConstraint(name = "uk_payout_merchant_key", columnNames = {"merchant_id", "idempotency_key"}))
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
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "provider_payout_id")
    private String providerPayoutId;

    protected PayoutBatch() {
    }

    public PayoutBatch(UUID merchantId, long amount, String currency, String idempotencyKey) {
        this.payoutBatchId = UUID.randomUUID();
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = PayoutStatus.CREATED;
        this.createdAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
    }

    public void markPaid() {
        markPaid("simulated-bank-" + payoutBatchId);
    }

    public void markPaid(String providerPayoutId) {
        if (status != PayoutStatus.PROCESSING) throw new IllegalStateException("Payout is not processing");
        status = PayoutStatus.PAID;
        this.providerPayoutId = providerPayoutId;
        paidAt = Instant.now();
    }

    public void markUnknown() {
        if (status != PayoutStatus.PROCESSING) throw new IllegalStateException("Payout is not processing");
        status = PayoutStatus.UNKNOWN;
    }

    public void reconcileFailed() {
        if (status != PayoutStatus.UNKNOWN) throw new IllegalStateException("Only unknown payout can be reconciled");
        status = PayoutStatus.FAILED;
    }

    /** Zwrot bankowy jest nowym faktem po wcześniejszym, poprawnym PAID. */
    public void returnedByBank() {
        if (status != PayoutStatus.PAID) throw new IllegalStateException("Only paid payout can be returned");
        status = PayoutStatus.RETURNED;
    }

    public void start() {
        if (status != PayoutStatus.CREATED) throw new IllegalStateException("Payout is not created");
        status = PayoutStatus.PROCESSING;
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

    public String getProviderPayoutId() { return providerPayoutId; }
}
