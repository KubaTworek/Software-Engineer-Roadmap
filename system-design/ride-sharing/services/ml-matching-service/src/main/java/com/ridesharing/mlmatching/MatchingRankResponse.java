package com.ridesharing.mlmatching;

import java.time.Instant;
import java.util.List;

public record MatchingRankResponse(String modelVersion, String rideId, List<RankedDriver> rankedDrivers, Instant rankedAt) {}
