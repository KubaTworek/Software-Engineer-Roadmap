package com.example.paymentsystem.operation;

import com.example.paymentsystem.payment.PaymentException;
import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Kanoniczny journal wywołań PSP/banku.
 *
 * <p>Rekord powstaje i zostaje zatwierdzony przed I/O. Dzięki temu awaria
 * procesu po wysłaniu requestu nie usuwa informacji o niejednoznacznym
 * efekcie. Unikalny klucz providera pozwala ponowić tę samą komendę, ale nie
 * tworzyć kolejnej logicznej operacji.</p>
 */
@Entity
@Table(name = "provider_operations", uniqueConstraints =
        @UniqueConstraint(name = "uk_provider_operation_key",
                columnNames = {"provider", "operation_type", "provider_idempotency_key"}))
public class ProviderOperation {
    @Id
    @Column(name = "operation_id", nullable = false)
    private UUID operationId;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private ProviderOperationType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private PaymentProvider provider;
    @Column(name = "provider_idempotency_key", nullable = false)
    private String providerIdempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProviderOperationStatus status;
    @Column(name = "provider_reference")
    private String providerReference;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderOperation() { }

    public ProviderOperation(UUID aggregateId, ProviderOperationType type,
                             PaymentProvider provider, String providerIdempotencyKey) {
        if (providerIdempotencyKey == null || providerIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Provider idempotency key is required");
        }
        this.operationId = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.type = type;
        this.provider = provider;
        this.providerIdempotencyKey = providerIdempotencyKey;
        this.status = ProviderOperationStatus.READY;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void start() {
        require(ProviderOperationStatus.READY);
        status = ProviderOperationStatus.IN_PROGRESS;
        touch();
    }

    public void succeed(String providerReference) {
        if (!(status == ProviderOperationStatus.IN_PROGRESS || status == ProviderOperationStatus.UNKNOWN)) {
            throw new PaymentException("Only an in-flight or unknown operation can succeed");
        }
        this.providerReference = providerReference;
        status = ProviderOperationStatus.SUCCEEDED;
        touch();
    }

    public void failBeforeDispatch(Throwable error) {
        require(ProviderOperationStatus.IN_PROGRESS);
        failureReason = error.getClass().getSimpleName();
        status = ProviderOperationStatus.FAILED;
        touch();
    }

    public void markUnknown(Throwable error) {
        require(ProviderOperationStatus.IN_PROGRESS);
        failureReason = error.getClass().getSimpleName();
        status = ProviderOperationStatus.UNKNOWN;
        touch();
    }

    /** Reconciliation może rozstrzygnąć UNKNOWN, ale nie może przepisać finalnej historii. */
    public void reconcileFailure(String reason) {
        require(ProviderOperationStatus.UNKNOWN);
        failureReason = reason;
        status = ProviderOperationStatus.FAILED;
        touch();
    }

    private void require(ProviderOperationStatus expected) {
        if (status != expected) throw new PaymentException("Expected " + expected + " but was " + status);
    }

    private void touch() { updatedAt = Instant.now(); }

    public UUID getOperationId() { return operationId; }
    public UUID getAggregateId() { return aggregateId; }
    public ProviderOperationType getType() { return type; }
    public ProviderOperationStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getProviderIdempotencyKey() { return providerIdempotencyKey; }
}
