package pl.jakubtworek.marketplace.payment.application;

import pl.jakubtworek.marketplace.payment.domain.Payment;

public interface PaymentRepository {
    Payment save(Payment payment);
}
