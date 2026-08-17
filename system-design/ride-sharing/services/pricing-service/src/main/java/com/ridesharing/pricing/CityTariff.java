package com.ridesharing.pricing;

import java.math.BigDecimal;

public record CityTariff(
        String cityId,
        String vehicleType,
        BigDecimal baseFare,
        BigDecimal perKm,
        BigDecimal perMinute,
        BigDecimal platformFeePercent,
        String currency
) {}
