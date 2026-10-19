package com.example.paymentsystem.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    Optional<Payment> findByProviderAndProviderPaymentId(PaymentProvider provider, String providerPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findProviderPaymentForUpdate(@Param("provider") PaymentProvider provider,
                                                    @Param("providerPaymentId") String providerPaymentId);

    List<Payment> findByMerchantId(UUID merchantId);

    List<Payment> findByProvider(PaymentProvider provider);

    long countByStatus(PaymentStatus status);

    long countByProvider(PaymentProvider provider);

    List<Payment> findByCreatedAtBetween(Instant from, Instant to);
}
