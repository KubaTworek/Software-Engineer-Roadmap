package com.example.paymentsystem.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaptureRepository extends JpaRepository<Capture, UUID> {
    Optional<Capture> findByPaymentIdAndIdempotencyKey(UUID paymentId, String idempotencyKey);
}
