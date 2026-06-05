package com.example.paymentsystem.payment.infrastructure.psp;

public record PspPaymentResponse(
        String providerPaymentId,
        String checkoutUrl
) {
}
