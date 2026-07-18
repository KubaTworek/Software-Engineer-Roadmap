package com.example.paymentsystem.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByReferenceTypeAndReferenceIdAndTransactionType(String referenceType, UUID referenceId, String transactionType);
}
