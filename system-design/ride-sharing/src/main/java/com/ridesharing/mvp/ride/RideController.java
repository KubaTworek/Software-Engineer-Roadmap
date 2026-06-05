package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {
    private final RideService rideService;

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('PASSENGER')")
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return rideService.estimate(request);
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public RideDto requestRide(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody RideRequest request) {
        return rideService.requestRide(principal.user(), request);
    }

    @GetMapping("/{rideId}")
    public RideDto get(@PathVariable UUID rideId) {
        return rideService.get(rideId);
    }

    @PostMapping("/{rideId}/cancel")
    public RideDto cancel(@AuthenticationPrincipal AuthenticatedUser principal,
                          @PathVariable UUID rideId,
                          @RequestBody(required = false) CancelRequest request) {
        return rideService.cancelByPassenger(principal.user(), rideId, request == null ? "No reason" : request.reason());
    }

    public record Point(@DecimalMin("-90.0") @DecimalMax("90.0") double lat,
                        @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
                        String address) {}
    public record EstimateRequest(Point pickup, Point dropoff, String vehicleType) {}
    public record EstimateResponse(BigDecimal estimatedPrice, String currency, double distanceKm, int durationMinutes) {}
    public record RideRequest(Point pickup, Point dropoff, String vehicleType, String paymentMethodId) {}
    public record CancelRequest(@NotBlank String reason) {}
}
