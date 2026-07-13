package com.ridesharing.mvp.payment;

import java.math.BigDecimal;

public interface PaymentProvider {
    ProviderPayment authorize(String idempotencyKey, BigDecimal amount, String currency);
    ProviderPayment capture(String providerPaymentId, BigDecimal amount, String currency);

    record ProviderPayment(String providerPaymentId, boolean success, String message) {}
}
