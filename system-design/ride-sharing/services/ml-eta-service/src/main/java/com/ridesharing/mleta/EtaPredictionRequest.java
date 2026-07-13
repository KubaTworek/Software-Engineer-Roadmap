package com.ridesharing.mleta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EtaPredictionRequest(
        @NotBlank String cityId,
        @NotBlank String vehicleType,
        @Valid @NotNull Coordinate origin,
        @Valid @NotNull Coordinate destination,
        Double routeDistanceKm,
        Integer hourOfDay,
        Integer weekday,
        Double trafficIndex,
        Double weatherPenalty
) {}
