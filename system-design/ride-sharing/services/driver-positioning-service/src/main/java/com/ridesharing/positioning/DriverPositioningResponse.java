package com.ridesharing.positioning;

import java.time.Instant;
import java.util.List;

public record DriverPositioningResponse(String driverId, String cityId, List<DriverPositioningRecommendation> recommendations, Instant generatedAt) {}
