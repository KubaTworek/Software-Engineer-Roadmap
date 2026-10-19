package com.ridesharing.mvp.location;

import com.ridesharing.mvp.driver.Driver;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Granica live-location używana identycznie przez profil lokalny i rozproszony. */
public interface DriverLocationPort {
    void update(Driver driver, LocationUpdate update);
    void remove(UUID driverId);
    List<UUID> nearby(String cityId, double lat, double lng, double radiusKm, int limit);

    record LocationUpdate(
            String cityId,
            double lat,
            double lng,
            double heading,
            double speed,
            double accuracyMeters,
            long sequence,
            Instant recordedAt
    ) {}
}
