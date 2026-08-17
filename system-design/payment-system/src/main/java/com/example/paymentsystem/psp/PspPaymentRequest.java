package com.example.paymentsystem.psp;

import java.util.UUID;

public record PspPaymentRequest(UUID paymentId, String orderId, long amount, String currency, String idempotencyKey) {
}
