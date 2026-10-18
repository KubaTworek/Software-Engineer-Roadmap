package com.example.paymentsystem.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID> { }
