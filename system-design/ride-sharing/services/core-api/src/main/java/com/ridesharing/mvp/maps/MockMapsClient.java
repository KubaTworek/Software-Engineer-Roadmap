package com.ridesharing.mvp.maps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mockowa implementacja klienta map.
 *
 * W MVP zastępuje zewnętrznego providera typu Google Maps, Mapbox, HERE albo OSRM.
 * Dzięki temu aplikację można uruchomić lokalnie bez kluczy API, kosztów i zależności
 * od zewnętrznych usług.
 *
 * Ta klasa nie zwraca prawdziwej trasy drogowej. Szacuje dystans i czas przejazdu
 * na podstawie odległości geograficznej między dwoma punktami.
 */
@Component
@ConditionalOnProperty(
        name = "app.maps.provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockMapsClient implements MapsClient {

    /**
     * Szacuje trasę między punktem startowym i docelowym.
     *
     * Flow:
     * 1. Liczy odległość w linii prostej metodą haversine.
     * 2. Mnoży ją przez 1.25, żeby zasymulować fakt, że droga ulicami
     *    jest zwykle dłuższa niż linia prosta.
     * 3. Szacuje czas przejazdu przy średniej miejskiej prędkości około 32 km/h.
     * 4. Zwraca RouteEstimate z dystansem w km i czasem w minutach.
     *
     * Wynik jest wystarczający dla MVP: pricing, ETA i demo flow przejazdu.
     * Nie powinien być traktowany jako dokładna nawigacja.
     */
    @Override
    public RouteEstimate estimateRoute(
            double originLat,
            double originLng,
            double destinationLat,
            double destinationLng
    ) {
        /*
         * Odległość haversine to dystans "po kuli ziemskiej" między punktami.
         * Mnożnik 1.25 jest uproszczonym road factor, który przybliża trasę ulicami.
         */
        double distance = haversineKm(
                originLat,
                originLng,
                destinationLat,
                destinationLng
        ) * 1.25;

        /*
         * Szacowany czas:
         * distance / 32 km/h * 60 minut.
         *
         * Math.max(3, ...) zabezpiecza przed absurdalnie krótkimi czasami
         * dla bardzo bliskich punktów.
         */
        int duration = Math.max(
                3,
                (int) Math.ceil(distance / 32.0 * 60)
        );

        return new RouteEstimate(round(distance), duration);
    }

    /**
     * Liczy odległość między dwoma punktami GPS metodą haversine.
     *
     * Ta metoda uwzględnia krzywiznę Ziemi i jest lepsza niż prosta geometria 2D
     * dla współrzędnych lat/lng.
     *
     * Wynik jest w kilometrach.
     */
    private static double haversineKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        /*
         * Średni promień Ziemi w kilometrach.
         */
        double r = 6371.0;

        /*
         * Różnice szerokości i długości geograficznej konwertujemy na radiany,
         * bo funkcje trygonometryczne w Javie pracują na radianach.
         */
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        /*
         * Wzór haversine.
         * Zwraca dystans po powierzchni kuli między dwoma punktami.
         */
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Zaokrągla dystans do dwóch miejsc po przecinku.
     *
     * Dzięki temu API nie zwraca niepotrzebnie długich wartości typu 4.238492384.
     */
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}