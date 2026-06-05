package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.driver.Driver;
import com.ridesharing.mvp.driver.DriverService;
import com.ridesharing.mvp.maps.MapsClient;
import com.ridesharing.mvp.matching.MatchingService;
import com.ridesharing.mvp.payment.PaymentService;
import com.ridesharing.mvp.user.AppUser;
import com.ridesharing.mvp.websocket.RideWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RideService {
    private final RideRepository rides;
    private final MapsClient mapsClient;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final DriverService driverService;
    private final RideWebSocketPublisher publisher;
    @Lazy
    private final MatchingService matchingService;

    public RideController.EstimateResponse estimate(RideController.EstimateRequest request) {
        var route = mapsClient.estimateRoute(request.pickup().lat(), request.pickup().lng(), request.dropoff().lat(), request.dropoff().lng());
        var price = pricingService.estimatePrice(route.distanceKm(), route.durationMinutes());
        return new RideController.EstimateResponse(price, "PLN", route.distanceKm(), route.durationMinutes());
    }

    @Transactional
    public RideDto requestRide(AppUser passenger, RideController.RideRequest request) {
        var route = mapsClient.estimateRoute(request.pickup().lat(), request.pickup().lng(), request.dropoff().lat(), request.dropoff().lng());
        var price = pricingService.estimatePrice(route.distanceKm(), route.durationMinutes());
        var now = Instant.now();
        var ride = Ride.builder()
                .id(UUID.randomUUID())
                .passenger(passenger)
                .status(RideStatus.MATCHING)
                .pickupLat(request.pickup().lat())
                .pickupLng(request.pickup().lng())
                .pickupAddress(request.pickup().address())
                .dropoffLat(request.dropoff().lat())
                .dropoffLng(request.dropoff().lng())
                .dropoffAddress(request.dropoff().address())
                .estimatedDistanceKm(BigDecimal.valueOf(route.distanceKm()))
                .estimatedDurationMinutes(route.durationMinutes())
                .estimatedPrice(price)
                .currency("PLN")
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        rides.save(ride);
        paymentService.authorize(ride);
        publisher.publishRideEvent(ride, "Ride requested; matching started");
        matchingService.matchAsync(ride, this);
        return RideDto.from(ride);
    }

    public RideDto get(UUID rideId) {
        return RideDto.from(find(rideId));
    }

    @Transactional
    public void offerDriver(UUID rideId, UUID driverId) {
        var ride = rides.findWithLockById(rideId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
        if (ride.getStatus() != RideStatus.MATCHING) {
            driverService.markAvailable(driverId);
            return;
        }
        var driver = driverService.getById(driverId);
        ride.setDriver(driver);
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
        ride.setAcceptedAt(Instant.now());
        rides.save(ride);
        publisher.publishRideEvent(ride, "Driver assigned. Waiting for explicit driver acceptance in MVP flow.");
    }

    @Transactional
    public RideDto acceptRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);
        assertDriverAssigned(ride, driver);
        if (ride.getStatus() != RideStatus.DRIVER_ASSIGNED) {
            throw new ApiException(HttpStatus.CONFLICT, "Ride is not waiting for driver acceptance");
        }
        ride.setStatus(RideStatus.DRIVER_ARRIVING);
        driverService.markOnTrip(driver.getId());
        rides.save(ride);
        publisher.publishRideEvent(ride, "Driver accepted and is arriving");
        return RideDto.from(ride);
    }

    @Transactional
    public RideDto rejectRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);
        assertDriverAssigned(ride, driver);
        ride.setDriver(null);
        ride.setStatus(RideStatus.MATCHING);
        driverService.markAvailable(driver.getId());
        rides.save(ride);
        publisher.publishRideEvent(ride, "Driver rejected; matching restarted");
        matchingService.matchAsync(ride, this);
        return RideDto.from(ride);
    }

    @Transactional
    public RideDto markArrived(Driver driver, UUID rideId) {
        var ride = locked(rideId);
        assertDriverAssigned(ride, driver);
        requireStatus(ride, RideStatus.DRIVER_ARRIVING);
        ride.setStatus(RideStatus.DRIVER_ARRIVED);
        ride.setDriverArrivedAt(Instant.now());
        rides.save(ride);
        publisher.publishRideEvent(ride, "Driver arrived");
        return RideDto.from(ride);
    }

    @Transactional
    public RideDto startRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);
        assertDriverAssigned(ride, driver);
        if (ride.getStatus() != RideStatus.DRIVER_ARRIVED && ride.getStatus() != RideStatus.DRIVER_ARRIVING) {
            throw new ApiException(HttpStatus.CONFLICT, "Ride cannot be started from status " + ride.getStatus());
        }
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        rides.save(ride);
        publisher.publishRideEvent(ride, "Ride started");
        return RideDto.from(ride);
    }

    @Transactional
    public RideDto completeRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);
        assertDriverAssigned(ride, driver);
        requireStatus(ride, RideStatus.IN_PROGRESS);
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());
        ride.setFinalPrice(ride.getEstimatedPrice());
        rides.save(ride);
        paymentService.capture(ride);
        driverService.markAvailable(driver.getId());
        publisher.publishRideEvent(ride, "Ride completed and payment captured");
        return RideDto.from(ride);
    }

    @Transactional
    public RideDto cancelByPassenger(AppUser passenger, UUID rideId, String reason) {
        var ride = locked(rideId);
        if (!ride.getPassenger().getId().equals(passenger.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Not your ride");
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Ride cannot be cancelled from status " + ride.getStatus());
        }
        var driver = ride.getDriver();
        ride.setStatus(RideStatus.CANCELLED_BY_PASSENGER);
        ride.setCancellationReason(reason);
        ride.setCancelledAt(Instant.now());
        rides.save(ride);
        if (driver != null) driverService.markAvailable(driver.getId());
        publisher.publishRideEvent(ride, "Ride cancelled by passenger");
        return RideDto.from(ride);
    }

    @Transactional
    public void markFailed(UUID rideId, String reason) {
        var ride = locked(rideId);
        if (ride.getStatus() != RideStatus.MATCHING) return;
        ride.setStatus(RideStatus.FAILED);
        ride.setCancellationReason(reason);
        rides.save(ride);
        publisher.publishRideEvent(ride, reason);
    }

    private Ride find(UUID rideId) {
        return rides.findById(rideId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    private Ride locked(UUID rideId) {
        return rides.findWithLockById(rideId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    private void assertDriverAssigned(Ride ride, Driver driver) {
        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Driver is not assigned to this ride");
        }
    }

    private void requireStatus(Ride ride, RideStatus status) {
        if (ride.getStatus() != status) throw new ApiException(HttpStatus.CONFLICT, "Expected status " + status + " but was " + ride.getStatus());
    }
}
