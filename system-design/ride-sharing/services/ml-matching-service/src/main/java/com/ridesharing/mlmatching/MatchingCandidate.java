package com.ridesharing.mlmatching;

public record MatchingCandidate(
        String driverId,
        double distanceKm,
        double pickupEtaMinutes,
        double driverRating,
        double acceptanceProbability,
        int recentCancellationCount,
        boolean sameDirection
) {}
