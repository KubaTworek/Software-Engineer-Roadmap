package com.ridesharing.mvp.resilience;

import com.ridesharing.mvp.maps.MapsClient;
import com.ridesharing.mvp.maps.RouteEstimate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Odporny adapter dla klienta map.
 *
 * W aplikacji ride-sharing wycena, ETA i matching zależą od estymacji trasy.
 * Jeżeli provider map chwilowo nie działa, cały flow zamawiania przejazdu
 * nie powinien od razu przestać działać.
 *
 * Ta klasa opakowuje MapsClient mechanizmami:
 * - retry,
 * - circuit breaker,
 * - fallback.
 */
@Component
@Primary
@RequiredArgsConstructor
public class ResilientMapsClient implements MapsClient {

    /**
     * Właściwa implementacja klienta map.
     *
     * W tym projekcie delegate to MockMapsClient, czyli lokalny provider MVP.
     * Produkcyjnie delegate powinien wskazywać np. na GoogleMapsClient, MapboxClient
     * albo OSRM/Valhalla adapter.
     *
     * @Primary sprawia, że Spring będzie domyślnie wstrzykiwał ResilientMapsClient
     * tam, gdzie wymagany jest MapsClient.
     */
    private final com.ridesharing.mvp.maps.MockMapsClient delegate;

    /**
     * Estymuje trasę między punktem początkowym i końcowym.
     *
     * Retry ponawia wywołanie przy błędach przejściowych.
     * Circuit breaker odcina kolejne wywołania, jeżeli provider map zaczyna masowo zawodzić.
     *
     * Jeżeli wywołanie nie powiedzie się po retry albo circuit breaker jest otwarty,
     * zostanie użyty fallbackEstimateRoute().
     */
    @Override
    @Retry(name = "maps")
    @CircuitBreaker(name = "maps", fallbackMethod = "fallbackEstimateRoute")
    public RouteEstimate estimateRoute(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng
    ) {
        return delegate.estimateRoute(fromLat, fromLng, toLat, toLng);
    }

    /**
     * Awaryjna estymacja trasy, gdy provider map jest niedostępny.
     *
     * Fallback:
     * - liczy przybliżony dystans na podstawie różnicy współrzędnych,
     * - zakłada minimum 1 km,
     * - estymuje czas przy średniej prędkości 30 km/h,
     * - zakłada minimum 5 minut.
     *
     * To nie jest dokładne ETA, ale pozwala aplikacji kontynuować podstawowy flow:
     * pokazać orientacyjną cenę, utworzyć przejazd albo nie zablokować requestu.
     */
    @SuppressWarnings("unused")
    RouteEstimate fallbackEstimateRoute(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng,
            Throwable ex
    ) {
        /*
         * Bardzo uproszczone przeliczenie stopni geograficznych na kilometry.
         * 1 stopień szerokości geograficznej to około 111 km.
         *
         * Math.hypot liczy prostą odległość euklidesową między punktami.
         * To mniej dokładne niż haversine, ale wystarczające jako awaryjny fallback.
         */
        double distanceKm = Math.max(
                1.0,
                Math.hypot(fromLat - toLat, fromLng - toLng) * 111.0
        );

        /*
         * Zakładamy średnią prędkość 30 km/h.
         * Minimum 5 minut chroni przed nielogicznie krótkim ETA.
         */
        int durationMinutes = Math.max(
                5,
                (int) Math.ceil(distanceKm / 30.0 * 60.0)
        );

        return new RouteEstimate(distanceKm, durationMinutes);
    }
}