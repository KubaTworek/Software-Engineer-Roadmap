package com.ridesharing.mleta;

import java.time.Instant;
import java.util.Map;

public record EtaPredictionResponse(
        String modelVersion,
        double etaMinutes,
        double p50Minutes,
        double p90Minutes,
        double confidence,
        Map<String, Double> featureContributions,
        Instant predictedAt
) {}
