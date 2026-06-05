package com.ridesharing.mvp.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {
    @Override
    public ProviderPayment authorize(String idempotencyKey, BigDecimal amount, String currency) {
        return new ProviderPayment("mock_auth_" + UUID.randomUUID(), true, "Authorized by mock provider");
    }

    @Override
    public ProviderPayment capture(String providerPaymentId, BigDecimal amount, String currency) {
        return new ProviderPayment(providerPaymentId, true, "Captured by mock provider");
    }
}
