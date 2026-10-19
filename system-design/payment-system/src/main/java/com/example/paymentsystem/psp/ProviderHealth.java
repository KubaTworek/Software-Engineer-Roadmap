package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.Duration;

@Entity
@Table(name = "provider_health")
public class ProviderHealth {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProviderHealthStatus status;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderHealth() {
    }

    public ProviderHealth(PaymentProvider provider) {
        this.provider = provider;
        this.status = ProviderHealthStatus.CLOSED;
        this.failureCount = 0;
        this.updatedAt = Instant.now();
    }

    public void recordSuccess() {
        failureCount = 0;
        status = ProviderHealthStatus.CLOSED;
        openedAt = null;
        updatedAt = Instant.now();
    }

    public void recordFailure(int threshold) {
        failureCount++;
        if (failureCount >= threshold) {
            status = ProviderHealthStatus.OPEN;
            openedAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    public void forceOpen() {
        status = ProviderHealthStatus.OPEN;
        openedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void forceClose() {
        status = ProviderHealthStatus.CLOSED;
        failureCount = 0;
        openedAt = null;
        updatedAt = Instant.now();
    }

    /** Only the caller changing OPEN to HALF_OPEN owns the single recovery probe. */
    public boolean acquirePermission(Instant now, Duration openDuration) {
        if (status == ProviderHealthStatus.CLOSED) return true;
        if (status == ProviderHealthStatus.OPEN && openedAt != null
                && !now.isBefore(openedAt.plus(openDuration))) {
            status = ProviderHealthStatus.HALF_OPEN;
            updatedAt = now;
            return true;
        }
        return false;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public ProviderHealthStatus getStatus() {
        return status;
    }

    public int getFailureCount() {
        return failureCount;
    }
}
