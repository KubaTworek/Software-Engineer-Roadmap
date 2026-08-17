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
        String ipCountry
) {
    public CaptureMode normalizedCaptureMode() {
        return captureMode == null ? CaptureMode.AUTOMATIC : captureMode;
    }
}
