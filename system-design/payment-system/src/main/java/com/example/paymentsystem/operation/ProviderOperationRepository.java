package com.example.paymentsystem.operation;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderOperationRepository extends JpaRepository<ProviderOperation, UUID> {
    Optional<ProviderOperation> findByProviderAndTypeAndProviderIdempotencyKey(
            PaymentProvider provider, ProviderOperationType type, String providerIdempotencyKey);
}
