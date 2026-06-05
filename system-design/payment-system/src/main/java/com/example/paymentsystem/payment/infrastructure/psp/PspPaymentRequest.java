package com.example.paymentsystem.payment.infrastructure.psp;

import java.util.UUID;

public record PspPaymentRequest(
        UUID paymentId,
        String orderId,
        long amount,
        String currency
) {
}
