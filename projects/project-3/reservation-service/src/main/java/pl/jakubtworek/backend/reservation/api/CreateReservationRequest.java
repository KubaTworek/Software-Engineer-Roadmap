package pl.jakubtworek.backend.reservation.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID eventId,
        @NotBlank String userId,
        @Min(1) int quantity
) {
}
