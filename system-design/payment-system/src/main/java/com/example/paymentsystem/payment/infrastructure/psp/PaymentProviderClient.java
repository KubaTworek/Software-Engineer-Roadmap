package com.example.paymentsystem.payment.infrastructure.psp;

public interface PaymentProviderClient {

    PspPaymentResponse createPayment(PspPaymentRequest request);
}
