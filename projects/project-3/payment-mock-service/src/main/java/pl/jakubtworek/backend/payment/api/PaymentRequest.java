package pl.jakubtworek.backend.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(@NotNull UUID orderId, @NotBlank String userId, @NotNull BigDecimal amount) {
}
