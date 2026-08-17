package com.example.paymentsystem.psp;

public record PspPaymentResponse(String providerPaymentId, String checkoutUrl) {
}
