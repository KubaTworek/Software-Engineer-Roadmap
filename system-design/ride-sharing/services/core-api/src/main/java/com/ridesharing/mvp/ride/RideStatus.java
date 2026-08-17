package com.ridesharing.mvp.ride;

public enum RideStatus {
    REQUESTED,
    MATCHING,
    DRIVER_ASSIGNED,
    DRIVER_ARRIVING,
    DRIVER_ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED_BY_PASSENGER,
    CANCELLED_BY_DRIVER,
    EXPIRED,
    FAILED
}
