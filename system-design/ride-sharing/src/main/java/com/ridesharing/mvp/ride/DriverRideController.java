package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.driver.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver/rides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverRideController {
    private final RideService rideService;
    private final DriverService driverService;

    @PostMapping("/{rideId}/accept")
    public RideDto accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID rideId) {
        var driver = driverService.getByUser(principal.user());
        return rideService.acceptRide(driver, rideId);
    }

    @PostMapping("/{rideId}/reject")
    public RideDto reject(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID rideId) {
        var driver = driverService.getByUser(principal.user());
        return rideService.rejectRide(driver, rideId);
    }

    @PostMapping("/{rideId}/arrived")
    public RideDto arrived(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID rideId) {
        var driver = driverService.getByUser(principal.user());
        return rideService.markArrived(driver, rideId);
    }

    @PostMapping("/{rideId}/start")
    public RideDto start(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID rideId) {
        var driver = driverService.getByUser(principal.user());
        return rideService.startRide(driver, rideId);
    }

    @PostMapping("/{rideId}/complete")
    public RideDto complete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID rideId) {
        var driver = driverService.getByUser(principal.user());
        return rideService.completeRide(driver, rideId);
    }
}
