package com.ridesharing.mvp.driver;

import java.math.BigDecimal;
import java.util.UUID;

public record DriverDto(
        UUID id,
        UUID userId,
        DriverVerificationStatus verificationStatus,
        DriverAvailabilityStatus availabilityStatus,
        String vehicleMake,
        String vehicleModel,
        String plateNumber,
        String vehicleColor,
        String vehicleType,
        BigDecimal rating
) {
    public static DriverDto from(Driver d) {
        return new DriverDto(d.getId(), d.getUser().getId(), d.getVerificationStatus(), d.getAvailabilityStatus(),
                d.getVehicleMake(), d.getVehicleModel(), d.getPlateNumber(), d.getVehicleColor(), d.getVehicleType(), d.getRating());
    }
}
