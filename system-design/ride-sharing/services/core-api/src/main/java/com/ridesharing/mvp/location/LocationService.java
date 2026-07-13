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

/**
 * Serwis odpowiedzialny za bieżącą lokalizację kierowców.
 *
 * W aplikacji ride-sharing to jeden z najważniejszych komponentów dla matchingu:
 * zapisuje aktualną pozycję kierowcy i pozwala szybko znaleźć dostępnych kierowców
 * w pobliżu pasażera.
 *
 * Dane lokalizacyjne są trzymane w Redisie, a nie w relacyjnej bazie,
 * bo lokalizacja zmienia się często i musi być odczytywana z niską latencją.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    /**
     * Redis GEO set zawierający tylko kierowców dostępnych dla matchingu.
     *
     * Ten klucz jest używany przez findNearbyAvailableDrivers().
     * Jeżeli kierowca nie jest AVAILABLE, nie powinien znajdować się w tym indeksie.
     */
    private static final String GEO_KEY = "drivers:geo:available";

    /**
     * Prefix dla osobnego hasha z pełniejszymi danymi lokalizacji kierowcy.
     *
     * Redis GEO przechowuje pozycję do wyszukiwania przestrzennego,
     * ale nie przechowuje np. heading, speed ani updatedAt.
     * Dlatego pełny snapshot lokalizacji trzymamy osobno jako hash.
     */
    private static final String LOCATION_KEY_PREFIX = "driver:location:";

    /**
     * RedisTemplate używany do zapisu:
     * - GEO indexu kierowców,
     * - hashy z aktualną lokalizacją,
     * - TTL dla danych lokalizacyjnych.
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Aktualizuje bieżącą lokalizację kierowcy.
     *
     * Flow:
     * 1. Zapisuje pełny snapshot lokalizacji do Redis Hash:
     *    lat, lng, heading, speed, updatedAt.
     * 2. Ustawia TTL na 120 sekund, żeby stare lokalizacje automatycznie wygasały.
     * 3. Jeżeli kierowca jest AVAILABLE, dodaje/aktualizuje go w Redis GEO indexie.
     *
     * To jest endpoint wysokiego ruchu — aplikacja kierowcy może wołać go co kilka sekund.
     * Dlatego nie powinien robić ciężkich operacji ani zapisywać każdego punktu do SQL.
     */
    public void updateDriverLocation(Driver driver, double lat, double lng, double heading, double speed) {
        var locationKey = LOCATION_KEY_PREFIX + driver.getId();
        var now = Instant.now().toString();

        /*
         * Pełna lokalizacja kierowcy.
         * Te dane są używane np. do pokazania kierowcy na mapie pasażera
         * oraz do prostego ETA/debugowania.
         */
        redisTemplate.opsForHash().put(locationKey, "lat", Double.toString(lat));
        redisTemplate.opsForHash().put(locationKey, "lng", Double.toString(lng));
        redisTemplate.opsForHash().put(locationKey, "heading", Double.toString(heading));
        redisTemplate.opsForHash().put(locationKey, "speed", Double.toString(speed));
        redisTemplate.opsForHash().put(locationKey, "updatedAt", now);

        /*
         * TTL chroni system przed używaniem starej lokalizacji.
         * Jeżeli aplikacja kierowcy przestanie wysyłać GPS, hash zniknie po 120 sekundach.
         */
        redisTemplate.expire(locationKey, 120, TimeUnit.SECONDS);

        /*
         * Do indeksu GEO trafiają tylko kierowcy dostępni.
         * MatchingService nie powinien brać pod uwagę kierowców OFFLINE, OFFERED_RIDE ani ON_TRIP.
         */
        if (driver.getAvailabilityStatus() == DriverAvailabilityStatus.AVAILABLE) {
            redisTemplate.opsForGeo().add(
                    GEO_KEY,
                    new Point(lng, lat),
                    driver.getId().toString()
            );
        }
    }

    /**
     * Usuwa kierowcę z live-location storage.
     *
     * Używane, gdy kierowca przechodzi np. na OFFLINE, ON_TRIP albo przestaje być dostępny.
     *
     * Usuwamy go z:
     * - GEO indexu, żeby nie był kandydatem w matchingu,
     * - hasha lokalizacji, żeby nie pokazywać starej pozycji.
     */
    public void removeDriver(UUID driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId.toString());
        redisTemplate.delete(LOCATION_KEY_PREFIX + driverId);
    }

    /**
     * Szuka dostępnych kierowców w promieniu radiusKm od punktu pasażera.
     *
     * Redis GEO wykonuje szybkie zapytanie przestrzenne po kluczu drivers:geo:available.
     * Wyniki są sortowane rosnąco po dystansie, więc najbliżsi kierowcy są pierwsi.
     *
     * limit ogranicza liczbę kandydatów przekazanych do matchingu.
     * To ważne, bo późniejszy ranking/ETA nie powinien liczyć setek kierowców,
     * jeśli wystarczy np. 10–20 najbliższych.
     */
    public List<UUID> findNearbyAvailableDrivers(double lat, double lng, double radiusKm, int limit) {
        var circle = new Circle(
                new Point(lng, lat),
                new Distance(radiusKm, Metrics.KILOMETERS)
        );

        var args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(limit);

        var results = redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

        if (results == null) {
            return List.of();
        }

        /*
         * Redis przechowuje driverId jako String.
         * MatchingService pracuje dalej na UUID kierowców.
         */
        return results.getContent().stream()
                .map(r -> UUID.fromString(Objects.requireNonNull(r.getContent().getName())))
                .toList();
    }

    /**
     * Pobiera ostatnią znaną lokalizację konkretnego kierowcy.
     *
     * Używane np. do:
     * - pokazania kierowcy pasażerowi na mapie,
     * - debugowania przejazdu,
     * - wyliczania prostego ETA,
     * - weryfikacji, czy lokalizacja nie jest zbyt stara.
     *
     * Jeżeli hash wygasł albo nie istnieje, zwracamy Optional.empty().
     */
    public Optional<DriverLocation> getLocation(UUID driverId) {
        var values = redisTemplate.opsForHash().entries(LOCATION_KEY_PREFIX + driverId);

        if (values.isEmpty()) {
            return Optional.empty();
        }

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