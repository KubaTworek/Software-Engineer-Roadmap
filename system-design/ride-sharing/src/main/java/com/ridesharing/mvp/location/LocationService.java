package com.ridesharing.mvp.location;

import com.ridesharing.mvp.driver.Driver;
import com.ridesharing.mvp.driver.DriverAvailabilityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LocationService {
    private static final String GEO_KEY = "drivers:geo:available";
    private static final String LOCATION_KEY_PREFIX = "driver:location:";
    private final RedisTemplate<String, String> redisTemplate;

    public void updateDriverLocation(Driver driver, double lat, double lng, double heading, double speed) {
        var locationKey = LOCATION_KEY_PREFIX + driver.getId();
        var now = Instant.now().toString();
        redisTemplate.opsForHash().put(locationKey, "lat", Double.toString(lat));
        redisTemplate.opsForHash().put(locationKey, "lng", Double.toString(lng));
        redisTemplate.opsForHash().put(locationKey, "heading", Double.toString(heading));
        redisTemplate.opsForHash().put(locationKey, "speed", Double.toString(speed));
        redisTemplate.opsForHash().put(locationKey, "updatedAt", now);
        redisTemplate.expire(locationKey, 120, TimeUnit.SECONDS);

        if (driver.getAvailabilityStatus() == DriverAvailabilityStatus.AVAILABLE) {
            redisTemplate.opsForGeo().add(GEO_KEY, new Point(lng, lat), driver.getId().toString());
        }
    }

    public void removeDriver(UUID driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId.toString());
        redisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
    }

    public List<UUID> findNearbyAvailableDrivers(double lat, double lng, double radiusKm, int limit) {
        var circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
        var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(limit);
        var results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);
        if (results == null) return List.of();
        return results.getContent().stream()
                .map(r -> UUID.fromString(Objects.requireNonNull(r.getContent().getName())))
                .toList();
    }

    public Optional<DriverLocation> getLocation(UUID driverId) {
        var values = redisTemplate.opsForHash().entries(LOCATION_KEY_PREFIX + driverId);
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(new DriverLocation(
                driverId,
                Double.parseDouble(values.get("lat").toString()),
                Double.parseDouble(values.get("lng").toString()),
                Double.parseDouble(values.getOrDefault("heading", "0").toString()),
                Double.parseDouble(values.getOrDefault("speed", "0").toString()),
                Instant.parse(values.get("updatedAt").toString())
        ));
    }
}
