package com.example.ecommerce.payment.dto;

import com.example.ecommerce.payment.PaymentStatus;

import java.math.BigDecimal;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            String provider,
            PaymentStatus status,
            BigDecimal amount,
            String currency
    ) {}
}
