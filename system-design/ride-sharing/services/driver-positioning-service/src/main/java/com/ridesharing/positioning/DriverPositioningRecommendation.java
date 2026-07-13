package com.ridesharing.positioning;

public record DriverPositioningRecommendation(String h3Cell, double lat, double lng, double expectedDemand, double repositionScore, String reason) {}
