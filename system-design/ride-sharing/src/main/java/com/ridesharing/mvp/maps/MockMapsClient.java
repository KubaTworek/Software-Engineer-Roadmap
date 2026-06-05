package com.ridesharing.mvp.maps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.maps.provider", havingValue = "mock", matchIfMissing = true)
public class MockMapsClient implements MapsClient {
    @Override
    public RouteEstimate estimateRoute(double originLat, double originLng, double destinationLat, double destinationLng) {
        double distance = haversineKm(originLat, originLng, destinationLat, destinationLng) * 1.25; // road factor
        int duration = Math.max(3, (int) Math.ceil(distance / 32.0 * 60)); // ~32 km/h city average
        return new RouteEstimate(round(distance), duration);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
