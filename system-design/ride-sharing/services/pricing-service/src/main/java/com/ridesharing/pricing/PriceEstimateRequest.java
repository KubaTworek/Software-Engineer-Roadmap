package com.ridesharing.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PriceEstimateRequest(
        @NotBlank String cityId,
        @NotBlank String vehicleType,
        @NotNull Double distanceKm,
        @NotNull Integer durationMinutes,
        @NotNull Integer activeRequests,
        @NotNull Integer availableDrivers
) {}
