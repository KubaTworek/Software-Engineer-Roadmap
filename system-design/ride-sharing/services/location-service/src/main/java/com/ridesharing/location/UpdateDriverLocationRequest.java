package com.ridesharing.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.time.Instant;

public record UpdateDriverLocationRequest(
        @NotBlank String driverId,
        @NotBlank String cityId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        Double heading,
        Double speed,
        Double accuracyMeters,
        @NotNull @PositiveOrZero Long sequence,
        @NotNull Instant recordedAt
) {}
