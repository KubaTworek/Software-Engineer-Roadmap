package com.example.paymentsystem.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID merchantId,
        @NotBlank String orderId,
        String customerId,
        @Positive long amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        CaptureMode captureMode,
        String customerCountry,
        String ipCountry,
        PaymentMethod paymentMethod
) {
    /** Backwards-compatible convenience constructor used by the existing labs. */
    public CreatePaymentRequest(UUID merchantId, String orderId, String customerId, long amount, String currency,
                                CaptureMode captureMode, String customerCountry, String ipCountry) {
        this(merchantId, orderId, customerId, amount, currency, captureMode, customerCountry, ipCountry,
                PaymentMethod.CARD);
    }

    public CaptureMode normalizedCaptureMode() {
        return captureMode == null ? CaptureMode.AUTOMATIC : captureMode;
    }

    public PaymentMethod normalizedPaymentMethod() {
        return paymentMethod == null ? PaymentMethod.CARD : paymentMethod;
    }
}
