package com.example.paymentsystem.payment.infrastructure.psp;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    @Override
    public PspPaymentResponse createPayment(PspPaymentRequest request) {
        String providerPaymentId = "mock_psp_" + UUID.randomUUID();
        String checkoutUrl = "https://mock-psp.local/checkout/" + providerPaymentId;

        return new PspPaymentResponse(providerPaymentId, checkoutUrl);
    }
}
