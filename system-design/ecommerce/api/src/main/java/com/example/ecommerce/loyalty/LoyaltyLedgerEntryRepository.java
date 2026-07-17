package com.example.ecommerce.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyLedgerEntryRepository extends JpaRepository<LoyaltyLedgerEntry, Long> {
    List<LoyaltyLedgerEntry> findByAccountIdOrderByIdDesc(Long accountId);
}
