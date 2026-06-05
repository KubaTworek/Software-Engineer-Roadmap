package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.location.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverController {
    private final DriverService driverService;
    private final LocationService locationService;

    @PostMapping("/profile")
    public DriverDto createOrUpdateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody DriverProfileRequest request) {
        return driverService.createOrUpdateProfile(principal.user(), request);
    }

    @PostMapping("/availability")
    public DriverDto updateAvailability(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody AvailabilityRequest request) {
        return driverService.updateAvailability(principal.user(), request.status());
    }

    @PostMapping("/location")
    public void updateLocation(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody LocationRequest request) {
        var driver = driverService.getByUser(principal.user());
        locationService.updateDriverLocation(driver, request.lat(), request.lng(), request.heading(), request.speed());
    }

    public record DriverProfileRequest(
            @NotBlank String vehicleMake,
            @NotBlank String vehicleModel,
            @NotBlank String plateNumber,
            @NotBlank String vehicleColor,
            @NotBlank String vehicleType
    ) {}

    public record AvailabilityRequest(@NotNull DriverAvailabilityStatus status) {}

    public record LocationRequest(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            double heading,
            double speed
    ) {}
}
