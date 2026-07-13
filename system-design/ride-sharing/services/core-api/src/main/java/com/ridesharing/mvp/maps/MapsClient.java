package com.ridesharing.mvp.maps;

public interface MapsClient {
    RouteEstimate estimateRoute(double originLat, double originLng, double destinationLat, double destinationLng);
}
