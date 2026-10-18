package com.example.paymentsystem.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Osobny rekord capture pozwala jednej autoryzacji mieć wiele częściowych
 * pobrań. Idempotencja należy do komendy capture, a nie do całej płatności.
 */
@Entity
@Table(name = "captures", uniqueConstraints =
        @UniqueConstraint(name = "uk_capture_payment_key", columnNames = {"payment_id", "idempotency_key"}))
public class Capture {
    @Id
    @Column(name = "capture_id", nullable = false)
    private UUID captureId;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "amount", nullable = false)
    private long amount;
    @Column(name = "currency", nullable = false)
    private String currency;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Capture() { }

    public Capture(UUID paymentId, long amount, String currency, String idempotencyKey) {
        this.captureId = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getCaptureId() { return captureId; }
    public UUID getPaymentId() { return paymentId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
}
