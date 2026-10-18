package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.payment.PaymentProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ProviderObservation(
        PaymentProvider provider,
        UUID paymentId,
        @NotBlank String providerPaymentId,
        @NotBlank String status,
        @Positive long amount,
        @NotBlank String currency
) { }
