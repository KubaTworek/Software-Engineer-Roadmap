package com.ridesharing.mvp.location;

import java.time.Instant;
import java.util.UUID;

public record DriverLocation(UUID driverId, double lat, double lng, double heading, double speed, Instant updatedAt) {}
