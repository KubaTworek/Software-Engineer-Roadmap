package com.example.ecommerce.payment;

import java.util.Optional;

/**
 * Production boundary do odpytywania PSP po timeoutach. Brak wyniku oznacza
 * nadal „unknown”, a nie porażkę płatności.
 */
public interface PaymentProviderClient {
    Optional<ProviderResult> lookup(Payment payment);

    record ProviderResult(String eventId, String providerPaymentId, PaymentStatus status) {}
}
