package com.example.paymentsystem.ledger;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @Column(name = "entry_id")
    private UUID entryId;
    @Column(name = "transaction_id")
    private UUID transactionId;
    @Column(name = "account_id")
    private String accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private LedgerDirection direction;
    @Column(name = "amount")
    private long amount;
    @Column(name = "currency")
    private String currency;
    @Column(name = "created_at")
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transactionId, String accountId, LedgerDirection direction, long amount, String currency) {
        this.entryId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
