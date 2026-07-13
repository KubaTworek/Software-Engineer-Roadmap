package com.ridesharing.mvp.ride;

import java.time.Instant;
import java.util.UUID;

public record RideEvent(UUID rideId, RideStatus status, UUID passengerId, UUID driverId, String message, Instant timestamp) {
    public static RideEvent of(Ride ride, String message) {
        return new RideEvent(ride.getId(), ride.getStatus(), ride.getPassenger().getId(),
                ride.getDriver() == null ? null : ride.getDriver().getId(), message, Instant.now());
    }
}
