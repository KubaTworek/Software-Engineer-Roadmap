package com.example.paymentsystem.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable description of a single PSP call. A payment and an attempt are
 * separate concepts: a timeout may leave the payment alive while the result
 * of this particular network operation is unknown.
 */
@Entity
@Table(name = "payment_attempts", uniqueConstraints =
        @UniqueConstraint(name = "uk_attempt_provider_key", columnNames = {"provider", "provider_idempotency_key"}))
public class PaymentAttempt {
    @Id
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PaymentProvider provider;
    @Column(name = "provider_idempotency_key", nullable = false)
    private String providerIdempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAttemptStatus status;
    @Column(name = "provider_payment_id")
    private String providerPaymentId;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID paymentId, PaymentProvider provider, String providerIdempotencyKey) {
        this.attemptId = UUID.randomUUID();
        this.paymentId = paymentId;
        this.provider = provider;
        this.providerIdempotencyKey = providerIdempotencyKey;
        this.status = PaymentAttemptStatus.READY;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void start() {
        if (status != PaymentAttemptStatus.READY) throw new PaymentException("Attempt is not ready");
        status = PaymentAttemptStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    public void succeed(String providerPaymentId) {
        if (status != PaymentAttemptStatus.IN_PROGRESS) throw new PaymentException("Attempt is not in progress");
        this.providerPaymentId = providerPaymentId;
        status = PaymentAttemptStatus.SUCCEEDED;
        updatedAt = Instant.now();
    }

    public void unknown(Throwable error) {
        if (status != PaymentAttemptStatus.IN_PROGRESS) throw new PaymentException("Attempt is not in progress");
        this.failureReason = error.getClass().getSimpleName();
        status = PaymentAttemptStatus.UNKNOWN;
        updatedAt = Instant.now();
    }

    /** The adapter proved that no request reached the provider. */
    public void failedBeforeDispatch(Throwable error) {
        if (status != PaymentAttemptStatus.IN_PROGRESS) throw new PaymentException("Attempt is not in progress");
        this.failureReason = error.getClass().getSimpleName();
        status = PaymentAttemptStatus.FAILED;
        updatedAt = Instant.now();
    }

    public UUID getAttemptId() { return attemptId; }
    public UUID getPaymentId() { return paymentId; }
    public PaymentAttemptStatus getStatus() { return status; }
    public String getProviderIdempotencyKey() { return providerIdempotencyKey; }
}
