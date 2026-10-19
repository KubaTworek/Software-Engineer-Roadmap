package com.ridesharing.mvp.payment;

import com.ridesharing.mvp.outbox.OutboxService;
import com.ridesharing.mvp.ride.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Krótkie transakcje stanu płatności. Provider jest wywoływany przed wejściem
 * tutaj, więc timeout sieciowy nie przetrzymuje połączenia ani blokady bazy.
 */
@Service
@RequiredArgsConstructor
public class PaymentPersistenceService {
    private final PaymentRepository payments;
    private final OutboxService outbox;

    @Transactional(readOnly = true)
    public Payment find(UUID rideId) {
        return payments.findByRideId(rideId).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordAuthorization(
            Ride ride,
            String idempotencyKey,
            PaymentProvider.ProviderPayment result
    ) {
        var existing = payments.findByRideId(ride.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = Instant.now();
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
        var saved = payments.saveAndFlush(payment);
        paymentEvent(saved, result.message());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment recordCapture(
            UUID paymentId,
            Ride ride,
            PaymentProvider.ProviderPayment result
    ) {
        var payment = payments.findLockedById(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return payment;
        }
        payment.setAmount(ride.getFinalPrice());
        payment.setStatus(result.success() ? PaymentStatus.CAPTURED : PaymentStatus.CAPTURE_FAILED);
        if (result.success()) {
            payment.setCapturedAt(Instant.now());
        } else {
            payment.setFailedAt(Instant.now());
        }
        paymentEvent(payment, result.message());
        return payment;
    }

    private void paymentEvent(Payment payment, String providerMessage) {
        outbox.paymentEvent(
                payment.getId(),
                switch (payment.getStatus()) {
                    case AUTHORIZED -> "PaymentAuthorized";
                    case AUTHORIZATION_FAILED -> "PaymentAuthorizationFailed";
                    case CAPTURED -> "PaymentCaptured";
                    case CAPTURE_FAILED -> "PaymentCaptureFailed";
                },
                Map.of(
                        "paymentId", payment.getId().toString(),
                        "rideId", payment.getRide().getId().toString(),
                        "status", payment.getStatus().name(),
                        "providerMessage", providerMessage == null ? "" : providerMessage
                )
        );
    }
}
