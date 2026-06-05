package com.example.paymentsystem.payment.api;

import com.example.paymentsystem.payment.domain.Payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String orderId,
        String customerId,
        long amount,
        String currency,
        String status,
        String provider,
        String providerPaymentId,
        String checkoutUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getProvider().name(),
                payment.getProviderPaymentId(),
                payment.getCheckoutUrl(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
