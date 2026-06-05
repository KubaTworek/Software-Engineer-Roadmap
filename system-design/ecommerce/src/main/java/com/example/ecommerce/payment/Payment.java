package com.example.ecommerce.payment;

import com.example.ecommerce.order.CustomerOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private CustomerOrder order;

    @Column(nullable = false)
    private String provider;

    private String providerPaymentId;

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

    public void markSucceeded(String providerPaymentId) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerPaymentId = providerPaymentId;
    }

    public void markFailed(String providerPaymentId) {
        this.status = PaymentStatus.FAILED;
        this.providerPaymentId = providerPaymentId;
    }
}
