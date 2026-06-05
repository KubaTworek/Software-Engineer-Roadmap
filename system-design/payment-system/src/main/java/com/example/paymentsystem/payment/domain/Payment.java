package com.example.paymentsystem.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, length = 80)
    private String orderId;

    @Column(name = "customer_id", length = 80)
    private String customerId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(name = "provider_payment_id", length = 120)
    private String providerPaymentId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(
            UUID paymentId,
            String orderId,
            String customerId,
            long amount,
            String currency,
            PaymentStatus status,
            PaymentProvider provider,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.provider = provider;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(String orderId, String customerId, long amount, String currency) {
        Instant now = Instant.now();

        return new Payment(
                UUID.randomUUID(),
                orderId,
                customerId,
                amount,
                currency.toUpperCase(),
                PaymentStatus.CREATED,
                PaymentProvider.MOCK_PSP,
                now,
                now
        );
    }

    public void markAsPending(String providerPaymentId, String checkoutUrl) {
        this.providerPaymentId = providerPaymentId;
        this.checkoutUrl = checkoutUrl;
        this.status = PaymentStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
