package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderHealthRepository extends JpaRepository<ProviderHealth, PaymentProvider> {
}
