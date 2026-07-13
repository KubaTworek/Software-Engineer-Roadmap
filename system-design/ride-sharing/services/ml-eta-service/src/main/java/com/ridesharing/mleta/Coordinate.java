package com.ridesharing.mleta;

import jakarta.validation.constraints.NotNull;

public record Coordinate(@NotNull Double lat, @NotNull Double lng) {}
