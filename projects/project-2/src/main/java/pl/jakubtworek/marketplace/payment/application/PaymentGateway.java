package pl.jakubtworek.marketplace.payment.application;

import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

public interface PaymentGateway {
    PaymentReservationResult reserve(UUID orderId, Money amount);
    record PaymentReservationResult(boolean accepted, String reason) {}
}
