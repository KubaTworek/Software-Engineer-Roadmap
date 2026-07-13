package com.ridesharing.location;

import java.time.Instant;

public record DriverLocationSnapshot(
        String driverId,
        String cityId,
        double lat,
        double lng,
        double heading,
        double speed,
        double accuracyMeters,
        String h3Cell,
        Instant updatedAt
) {}
