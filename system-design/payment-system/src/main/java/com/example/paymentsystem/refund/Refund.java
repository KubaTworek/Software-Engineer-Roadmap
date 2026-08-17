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

    protected Refund() {
    }

    public Refund(UUID paymentId, long amount, String currency, String reason, String idempotencyKey) {
        Instant now = Instant.now();
        this.refundId = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = RefundStatus.SUCCEEDED;
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
}
