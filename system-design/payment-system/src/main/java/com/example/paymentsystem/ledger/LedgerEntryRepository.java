package com.example.paymentsystem.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Query("select coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0) from LedgerEntry e where e.accountId = :accountId and e.currency = :currency")
    long balance(String accountId, String currency);
}
