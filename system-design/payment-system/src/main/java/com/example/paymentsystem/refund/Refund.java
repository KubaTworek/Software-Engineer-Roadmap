package com.example.paymentsystem.refund;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    @Column(name = "refund_id")
    private UUID refundId;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "amount")
    private long amount;
    @Column(name = "currency")
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RefundStatus status;
    @Column(name = "reason")
    private String reason;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "provider_refund_id")
    private String providerRefundId;

    protected Refund() {
    }

    public Refund(UUID paymentId, long amount, String currency, String reason, String idempotencyKey) {
        Instant now = Instant.now();
        this.refundId = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = RefundStatus.PROCESSING;
        this.reason = reason;
        this.createdAt = now;
        this.updatedAt = now;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public void succeed(String providerRefundId) {
        if (!(status == RefundStatus.PROCESSING || status == RefundStatus.UNKNOWN)) {
            throw new IllegalStateException("Refund cannot succeed from " + status);
        }
        this.providerRefundId = providerRefundId;
        this.status = RefundStatus.SUCCEEDED;
        this.updatedAt = Instant.now();
    }

    public void unknown() {
        if (status != RefundStatus.PROCESSING) throw new IllegalStateException("Refund is not processing");
        status = RefundStatus.UNKNOWN;
        updatedAt = Instant.now();
    }

    public void failed() {
        if (status != RefundStatus.PROCESSING) throw new IllegalStateException("Refund is not processing");
        status = RefundStatus.FAILED;
        updatedAt = Instant.now();
    }

    public RefundStatus getStatus() { return status; }
    public String getProviderRefundId() { return providerRefundId; }
}
