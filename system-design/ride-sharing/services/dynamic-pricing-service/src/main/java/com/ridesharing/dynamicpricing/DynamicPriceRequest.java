package com.ridesharing.dynamicpricing;

import java.math.BigDecimal;

public record DynamicPriceRequest(String cityId, String h3Cell, String vehicleType, double distanceKm, int durationMinutes, int activeRequests, int availableDrivers, BigDecimal basePrice) {}
