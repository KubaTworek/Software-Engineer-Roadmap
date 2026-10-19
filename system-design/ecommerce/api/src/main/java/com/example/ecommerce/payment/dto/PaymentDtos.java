package com.example.ecommerce.payment.dto;

import com.example.ecommerce.payment.PaymentStatus;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    public record ProviderWebhookEvent(
            @NotBlank String eventId,
            @NotNull Long paymentId,
            @NotBlank String providerPaymentId,
            @NotNull PaymentStatus status,
            @NotNull BigDecimal amount,
            @NotBlank String currency
    ) {}
}
