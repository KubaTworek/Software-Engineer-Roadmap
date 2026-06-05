package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.location.LocationService;
import com.ridesharing.mvp.user.AppUser;
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
public class DriverService {
    private final DriverRepository drivers;
    @Lazy
    private final LocationService locationService;

    public Driver getByUser(AppUser user) {
        return drivers.findByUser(user).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver profile not found"));
    }

    public Driver getById(UUID id) {
        return drivers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    @Transactional
    public DriverDto createOrUpdateProfile(AppUser user, DriverController.DriverProfileRequest request) {
        var driver = drivers.findByUser(user).orElseGet(() -> Driver.builder()
                .id(UUID.randomUUID())
                .user(user)
                .verificationStatus(DriverVerificationStatus.VERIFIED) // MVP shortcut; real system should verify documents.
                .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
                .rating(BigDecimal.valueOf(5.00))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        driver.setVehicleMake(request.vehicleMake());
        driver.setVehicleModel(request.vehicleModel());
        driver.setPlateNumber(request.plateNumber());
        driver.setVehicleColor(request.vehicleColor());
        driver.setVehicleType(request.vehicleType());
        return DriverDto.from(drivers.save(driver));
    }

    @Transactional
    public DriverDto updateAvailability(AppUser user, DriverAvailabilityStatus status) {
        var driver = getByUser(user);
        if (driver.getVerificationStatus() != DriverVerificationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Driver is not verified");
        }
        driver.setAvailabilityStatus(status);
        drivers.save(driver);
        if (status != DriverAvailabilityStatus.AVAILABLE) {
            locationService.removeDriver(driver.getId());
        }
        return DriverDto.from(driver);
    }

    @Transactional
    public boolean tryOfferDriver(UUID driverId) {
        var driver = drivers.findWithLockById(driverId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver not found"));
        if (driver.getAvailabilityStatus() != DriverAvailabilityStatus.AVAILABLE) return false;
        driver.setAvailabilityStatus(DriverAvailabilityStatus.OFFERED_RIDE);
        drivers.save(driver);
        return true;
    }

    @Transactional
    public void markAvailable(UUID driverId) {
        var driver = getById(driverId);
        driver.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        drivers.save(driver);
    }

    @Transactional
    public void markOnTrip(UUID driverId) {
        var driver = getById(driverId);
        driver.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        drivers.save(driver);
    }
}
