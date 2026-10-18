package com.example.ecommerce.payment;

import com.example.ecommerce.order.CustomerOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_payment_provider_event", columnNames = "provider_event_id")
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(nullable = false)
    private String provider;

    private String providerPaymentId;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Payment() {}

    public Payment(CustomerOrder order, String provider, BigDecimal amount, String currency, String idempotencyKey) {
        this.order = order;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public String getProvider() { return provider; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProviderEventId() { return providerEventId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSucceeded(String providerPaymentId) {
        requirePending("succeed");
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
    }

    public void markFailed(String providerPaymentId) {
        requirePending("fail");
        this.status = PaymentStatus.FAILED;
        this.providerPaymentId = providerPaymentId;
    }

    public void rememberProviderEvent(String providerEventId) {
        if (this.providerEventId != null && !this.providerEventId.equals(providerEventId)) {
            throw new IllegalStateException("Payment already handled by another provider event");
        }
        this.providerEventId = providerEventId;
    }

    private void requirePending(String transition) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot " + transition + " payment in status " + status);
        }
    }
}
