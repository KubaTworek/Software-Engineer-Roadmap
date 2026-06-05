package com.ridesharing.mvp.payment;

import com.ridesharing.mvp.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentProvider provider;

    @Transactional
    public Payment authorize(Ride ride) {
        var existing = payments.findByRideId(ride.getId());
        if (existing.isPresent()) return existing.get();
        var idempotencyKey = "authorize:" + ride.getId();
        var result = provider.authorize(idempotencyKey, ride.getEstimatedPrice(), ride.getCurrency());
        var now = Instant.now();
        var payment = Payment.builder()
                .id(UUID.randomUUID())
                .ride(ride)
                .passenger(ride.getPassenger())
                .amount(ride.getEstimatedPrice())
                .currency(ride.getCurrency())
                .provider("mock")
                .providerPaymentId(result.providerPaymentId())
                .idempotencyKey(idempotencyKey)
                .status(result.success() ? PaymentStatus.AUTHORIZED : PaymentStatus.AUTHORIZATION_FAILED)
                .authorizedAt(result.success() ? now : null)
                .failedAt(result.success() ? null : now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return payments.save(payment);
    }

    @Transactional
    public Payment capture(Ride ride) {
        var payment = payments.findByRideId(ride.getId()).orElseGet(() -> authorize(ride));
        var result = provider.capture(payment.getProviderPaymentId(), ride.getFinalPrice(), ride.getCurrency());
        payment.setAmount(ride.getFinalPrice());
        payment.setStatus(result.success() ? PaymentStatus.CAPTURED : PaymentStatus.CAPTURE_FAILED);
        if (result.success()) payment.setCapturedAt(Instant.now()); else payment.setFailedAt(Instant.now());
        return payments.save(payment);
    }
}
