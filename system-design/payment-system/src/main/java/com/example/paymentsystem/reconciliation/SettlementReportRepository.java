package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReport, String> {
    Optional<SettlementReport> findByProviderAndExternalReportId(PaymentProvider provider, String externalReportId);
}
