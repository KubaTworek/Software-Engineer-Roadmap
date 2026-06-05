package com.ridesharing.mvp.matching;

import com.ridesharing.mvp.driver.DriverService;
import com.ridesharing.mvp.location.LocationService;
import com.ridesharing.mvp.ride.Ride;
import com.ridesharing.mvp.ride.RideService;
import com.ridesharing.mvp.ride.RideStatus;
import com.ridesharing.mvp.websocket.RideWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingService {
    private final LocationService locationService;
    private final DriverService driverService;
    private final RideWebSocketPublisher publisher;

    @Value("${app.matching.initial-radius-km:3}")
    private double radiusKm;

    @Value("${app.matching.max-candidates:10}")
    private int maxCandidates;

    @Async
    public void matchAsync(Ride ride, RideService rideService) {
        var candidates = locationService.findNearbyAvailableDrivers(ride.getPickupLat(), ride.getPickupLng(), radiusKm, maxCandidates);
        for (var driverId : candidates) {
            if (driverService.tryOfferDriver(driverId)) {
                rideService.offerDriver(ride.getId(), driverId);
                return;
            }
        }
        rideService.markFailed(ride.getId(), "No available drivers nearby");
    }
}
