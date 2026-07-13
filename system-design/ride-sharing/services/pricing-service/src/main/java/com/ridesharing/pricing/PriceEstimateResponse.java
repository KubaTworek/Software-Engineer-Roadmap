package com.ridesharing.pricing;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceEstimateResponse(
        String cityId,
        String vehicleType,
        BigDecimal estimatedPrice,
        BigDecimal driverEarnings,
        BigDecimal surgeMultiplier,
        String currency,
        Instant expiresAt
) {}
