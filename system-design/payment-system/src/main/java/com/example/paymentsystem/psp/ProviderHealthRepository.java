package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProviderHealthRepository extends JpaRepository<ProviderHealth, PaymentProvider> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderHealth> findLockedByProvider(PaymentProvider provider);
}
