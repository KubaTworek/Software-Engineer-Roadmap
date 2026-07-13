package com.ridesharing.demand;

import java.time.Instant;
import java.util.Map;

public record DemandForecastResponse(String cityId, String h3Cell, int horizonMinutes, double expectedRequests, double confidence, Map<String, Double> signals, Instant predictedAt) {}
