package com.ridesharing.mlmatching;

import java.util.Map;

public record RankedDriver(String driverId, double score, String decisionReason, Map<String, Double> features) {}
