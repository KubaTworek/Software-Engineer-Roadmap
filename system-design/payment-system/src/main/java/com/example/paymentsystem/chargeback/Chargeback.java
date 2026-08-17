package com.example.paymentsystem.chargeback;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chargebacks")
public class Chargeback {
    @Id
    @Column(name = "chargeback_id")
    private UUID chargebackId;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "amount")
    private long amount;
    @Column(name = "currency")
    private String currency;
    @Column(name = "reason")
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ChargebackStatus status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Chargeback() {
    }

    public Chargeback(UUID paymentId, long amount, String currency, String reason) {
        Instant now = Instant.now();
        this.chargebackId = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.status = ChargebackStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void win() {
        status = ChargebackStatus.WON;
        updatedAt = Instant.now();
    }

    public void lose() {
        status = ChargebackStatus.LOST;
        updatedAt = Instant.now();
    }

    public UUID getChargebackId() {
        return chargebackId;
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

    public ChargebackStatus getStatus() {
        return status;
    }
}
