package pl.jakubtworek.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record EventCreateRequest(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String category,
        @NotNull @Future OffsetDateTime startsAt,
        @Min(1) int totalCapacity
) {
}
