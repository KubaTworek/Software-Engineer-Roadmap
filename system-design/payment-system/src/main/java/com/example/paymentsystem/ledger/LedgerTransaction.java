package com.example.paymentsystem.ledger;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;
    @Column(name = "reference_type")
    private String referenceType;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(name = "transaction_type")
    private String transactionType;
    @Column(name = "created_at")
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(String referenceType, UUID referenceId, String transactionType) {
        this.transactionId = UUID.randomUUID();
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.transactionType = transactionType;
        this.createdAt = Instant.now();
    }

    public UUID getTransactionId() {
        return transactionId;
    }
}
