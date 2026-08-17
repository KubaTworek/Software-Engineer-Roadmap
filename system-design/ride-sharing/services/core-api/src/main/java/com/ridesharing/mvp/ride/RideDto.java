package com.ridesharing.mvp.ride;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RideDto(
        UUID id,
        UUID passengerId,
        UUID driverId,
        RideStatus status,
        double pickupLat,
        double pickupLng,
        String pickupAddress,
        double dropoffLat,
        double dropoffLng,
        String dropoffAddress,
        BigDecimal estimatedDistanceKm,
        Integer estimatedDurationMinutes,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice,
        String currency,
        Instant requestedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt
) {
    public static RideDto from(Ride r) {
        return new RideDto(r.getId(), r.getPassenger().getId(), r.getDriver() == null ? null : r.getDriver().getId(), r.getStatus(),
                r.getPickupLat(), r.getPickupLng(), r.getPickupAddress(), r.getDropoffLat(), r.getDropoffLng(), r.getDropoffAddress(),
                r.getEstimatedDistanceKm(), r.getEstimatedDurationMinutes(), r.getEstimatedPrice(), r.getFinalPrice(), r.getCurrency(),
                r.getRequestedAt(), r.getAcceptedAt(), r.getStartedAt(), r.getCompletedAt());
    }
}
