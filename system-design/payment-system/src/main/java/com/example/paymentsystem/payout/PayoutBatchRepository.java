package com.example.paymentsystem.payout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID> {
    List<PayoutBatch> findByMerchantId(UUID merchantId);
}
