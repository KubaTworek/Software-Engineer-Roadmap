package com.ridesharing.dynamicpricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record DynamicPriceResponse(String cityId, String h3Cell, BigDecimal finalPrice, BigDecimal surgeMultiplier, BigDecimal driverEarnings, Map<String, Double> signals, String guardrail, Instant pricedAt) {}
