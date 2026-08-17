package com.ridesharing.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDriverLocationRequest(
        @NotBlank String driverId,
        @NotBlank String cityId,
        @NotNull Double lat,
        @NotNull Double lng,
        Double heading,
        Double speed,
        Double accuracyMeters
) {}
