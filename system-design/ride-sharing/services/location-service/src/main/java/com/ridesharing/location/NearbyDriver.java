package com.ridesharing.location;

import java.time.Instant;

public record NearbyDriver(
        String driverId,
        String cityId,
        double lat,
        double lng,
        double distanceKm,
        String h3Cell,
        Instant updatedAt
) {}
