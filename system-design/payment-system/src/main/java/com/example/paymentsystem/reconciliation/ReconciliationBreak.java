package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_breaks")
public class ReconciliationBreak {
    @Id @Column(name = "reconciliation_break_id") private UUID id;
    @Column(name = "payment_id") private UUID paymentId;
    @Enumerated(EnumType.STRING) @Column(name = "provider") private PaymentProvider provider;
    @Column(name = "provider_payment_id") private String providerPaymentId;
    @Column(name = "break_type") private String breakType;
    @Column(name = "expected_value") private String expectedValue;
    @Column(name = "actual_value") private String actualValue;
    @Column(name = "status") private String status;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected ReconciliationBreak() { }

    public ReconciliationBreak(UUID paymentId, PaymentProvider provider, String providerPaymentId,
                               String breakType, String expectedValue, String actualValue) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.breakType = breakType;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBreakType() { return breakType; }
}
