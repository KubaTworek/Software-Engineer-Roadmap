package pl.jakubtworek.backend.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID reservationId, @NotBlank String userId) {
}
