package pl.jakubtworek.marketplace.payment.application;

import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findByOrderId(UUID orderId);
    boolean updateIfStatus(Payment payment, PaymentStatus expected);
}
