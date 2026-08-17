package com.ridesharing.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Serwis lokalizacji kierowców w osobnym Location Service.
 *
 * To jeden z kluczowych komponentów skalowania ride-sharingu.
 * Obsługuje wysokoczęstotliwościowe update'y GPS oraz szybkie wyszukiwanie
 * dostępnych kierowców w pobliżu punktu odbioru.
 *
 * W tej implementacji Redis pełni rolę live-store:
 * - hash per driver trzyma ostatni snapshot lokalizacji,
 * - set per H3 cell trzyma driverId w danej komórce,
 * - Redis GEO trzyma pozycję kierowcy per city.
 *
 * LocationService nie powinien być traktowany jako trwała historia lokalizacji.
 * To warstwa realtime/near-realtime.
 */
@Service
public class LocationService {

    /**
     * Redis używany jako szybki store dla bieżących lokalizacji.
     *
     * StringRedisTemplate operuje na stringach, więc snapshot lokalizacji jest ręcznie
     * zapisywany do hash mapy jako pola tekstowe.
     */
    private final StringRedisTemplate redis;

    /**
     * Indexer geograficzny, np. H3.
     *
     * Zamienia lat/lng na komórkę przestrzenną i pozwala pobrać sąsiednie komórki
     * przy wyszukiwaniu kierowców w pobliżu.
     */
    private final GeoCellIndexer indexer;

    /**
     * Publisher eventów lokalizacji do Kafki.
     *
     * Dzięki temu inne serwisy, np. Fraud, Warehouse albo Demand/Positioning,
     * mogą reagować na zmiany lokalizacji bez bezpośredniego odpytywania Redis.
     */
    private final LocationEventPublisher publisher;

    /**
     * Po ilu sekundach lokalizacja kierowcy jest uznawana za nieświeżą.
     *
     * Domyślnie 30 sekund.
     * To chroni matching przed przypisaniem kierowcy, którego GPS dawno nie był aktualizowany.
     */
    private final int staleAfterSeconds;

    /**
     * Konstruktor wstrzykujący Redis, indexer, publisher i konfigurację stale timeout.
     */
    public LocationService(
            StringRedisTemplate redis,
            GeoCellIndexer indexer,
            LocationEventPublisher publisher,
            @Value("${app.geo.stale-after-seconds:30}") int staleAfterSeconds
    ) {
        this.redis = redis;
        this.indexer = indexer;
        this.publisher = publisher;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /**
     * Aktualizuje lokalizację kierowcy.
     *
     * Flow:
     * 1. Przelicza lat/lng na H3 cell.
     * 2. Buduje DriverLocationSnapshot.
     * 3. Zapisuje snapshot do Redis Hash per driver.
     * 4. Ustawia TTL na hash kierowcy.
     * 5. Dodaje driverId do seta danej komórki H3.
     * 6. Dodaje pozycję do Redis GEO per city.
     * 7. Publikuje event lokalizacji do Kafki.
     * 8. Zwraca zapisany snapshot.
     *
     * Ten endpoint będzie wywoływany bardzo często, więc logika musi być lekka.
     */
    public DriverLocationSnapshot update(UpdateDriverLocationRequest request) {
        /*
         * Komórka H3 dla bieżącej pozycji kierowcy.
         * Służy do szybkiego zawężenia nearby search do lokalnych obszarów.
         */
        var cell = indexer.cell(
                request.lat(),
                request.lng()
        );

        /*
         * Snapshot ostatniej znanej lokalizacji kierowcy.
         *
         * Jeśli heading/speed/accuracyMeters nie są podane,
         * zapisujemy 0 jako wartość domyślną.
         */
        var snapshot = new DriverLocationSnapshot(
                request.driverId(),
                request.cityId(),
                request.lat(),
                request.lng(),
                request.heading() == null ? 0 : request.heading(),
                request.speed() == null ? 0 : request.speed(),
                request.accuracyMeters() == null ? 0 : request.accuracyMeters(),
                cell,
                Instant.now()
        );

        /*
         * Redis Hash z ostatnim snapshotem kierowcy.
         *
         * Klucz prawdopodobnie ma format typu:
         * driver:location:{driverId}
         *
         * Hash pozwala szybko odczytać wszystkie pola ostatniej lokalizacji.
         */
        var hash = new LinkedHashMap<String, String>();
        hash.put("driverId", snapshot.driverId());
        hash.put("cityId", snapshot.cityId());
        hash.put("lat", Double.toString(snapshot.lat()));
        hash.put("lng", Double.toString(snapshot.lng()));
        hash.put("heading", Double.toString(snapshot.heading()));
        hash.put("speed", Double.toString(snapshot.speed()));
        hash.put("accuracyMeters", Double.toString(snapshot.accuracyMeters()));
        hash.put("h3Cell", snapshot.h3Cell());
        hash.put("updatedAt", snapshot.updatedAt().toString());

        /*
         * Zapis ostatniego snapshotu kierowcy.
         */
        redis.opsForHash().putAll(
                LocationKeys.driver(snapshot.driverId()),
                hash
        );

        /*
         * TTL hash-a jest dłuższy niż staleAfterSeconds.
         *
         * Dzięki temu możemy jeszcze przez chwilę odczytać snapshot,
         * ale nearby() i tak odrzuci go jako stale po staleAfterSeconds.
         */
        redis.expire(
                LocationKeys.driver(snapshot.driverId()),
                Duration.ofSeconds(staleAfterSeconds * 4L)
        );

        /*
         * Dodanie kierowcy do seta komórki H3.
         *
         * Set per city + h3Cell pozwala szybko zebrać kandydatów z komórek sąsiednich.
         */
        redis.opsForSet().add(
                LocationKeys.cell(snapshot.cityId(), snapshot.h3Cell()),
                snapshot.driverId()
        );

        /*
         * TTL dla seta komórki.
         *
         * Uwaga: to nie usuwa pojedynczego driverId z poprzedniej komórki,
         * jeśli kierowca się przemieścił.
         */
        redis.expire(
                LocationKeys.cell(snapshot.cityId(), snapshot.h3Cell()),
                Duration.ofMinutes(10)
        );

        /*
         * Redis GEO per city.
         *
         * Dodaje lub aktualizuje pozycję kierowcy w indeksie GEO.
         *
         * W tej wersji nearby() nie używa Redis GEO do pobierania kandydatów,
         * tylko setów H3. GEO może być użyte jako dodatkowy indeks albo do debugowania.
         */
        redis.opsForGeo().add(
                LocationKeys.available(snapshot.cityId()),
                new RedisGeoCommands.GeoLocation<>(
                        snapshot.driverId(),
                        new Point(snapshot.lng(), snapshot.lat())
                )
        );

        /*
         * Publikacja eventu do Kafki.
         *
         * To pozwala Fraud/Warehouse/Demand reagować asynchronicznie.
         * Sama publikacja nie jest jednak transactional względem zapisu do Redis.
         */
        publisher.publish(snapshot);

        return snapshot;
    }

    /**
     * Wyszukuje dostępnych kierowców w pobliżu wskazanego punktu.
     *
     * Flow:
     * 1. Wyznacza pobliskie komórki H3 dla pickup lat/lng.
     * 2. Zbiera driverId z setów Redis dla tych komórek.
     * 3. Ogranicza liczbę kandydatów do około limit * 3.
     * 4. Odczytuje snapshot każdego kierowcy.
     * 5. Odrzuca brakujące, niepoprawne i nieświeże snapshoty.
     * 6. Liczy dystans Haversine.
     * 7. Sortuje po dystansie.
     * 8. Zwraca maksymalnie limit kierowców.
     *
     * To jest candidate generation dla Matching Service.
     * Finalny matching powinien jeszcze sprawdzić status kierowcy, typ pojazdu,
     * lock/rezerwację i reguły biznesowe.
     */
    public List<NearbyDriver> nearby(
            String cityId,
            double lat,
            double lng,
            int ringSize,
            int limit
    ) {
        /*
         * LinkedHashSet usuwa duplikaty i zachowuje kolejność dodawania.
         * Ten sam kierowca może pojawić się w kilku strukturach Redis,
         * szczególnie jeśli nie usuwamy go ze starych komórek.
         */
        Set<String> candidateIds = new LinkedHashSet<>();

        /*
         * Szukamy kierowców w komórce bazowej i sąsiednich komórkach H3.
         */
        for (String cell : indexer.nearbyCells(lat, lng, ringSize)) {
            var members = redis.opsForSet().members(
                    LocationKeys.cell(cityId, cell)
            );

            if (members != null) {
                candidateIds.addAll(members);
            }

            /*
             * Pobieramy więcej kandydatów niż limit, bo część z nich może odpaść:
             * - snapshot wygasł,
             * - lokalizacja jest stale,
             * - cityId nie pasuje,
             * - dane są uszkodzone.
             */
            if (candidateIds.size() >= limit * 3L) {
                break;
            }
        }

        return candidateIds.stream()
                /*
                 * Odczyt ostatniego snapshotu każdego kierowcy.
                 */
                .map(this::read)
                .flatMap(Optional::stream)

                /*
                 * Bezpieczeństwo: upewniamy się, że snapshot nadal należy do szukanego miasta.
                 */
                .filter(s -> s.cityId().equals(cityId))

                /*
                 * Kluczowy filtr świeżości.
                 *
                 * Kierowca z lokalizacją starszą niż staleAfterSeconds
                 * nie powinien być zwracany do matchingu.
                 */
                .filter(s -> Duration
                        .between(s.updatedAt(), Instant.now())
                        .getSeconds() <= staleAfterSeconds)

                /*
                 * Zamiana snapshotu na NearbyDriver z obliczonym dystansem do pickup pointu.
                 */
                .map(s -> new NearbyDriver(
                        s.driverId(),
                        s.cityId(),
                        s.lat(),
                        s.lng(),
                        haversineKm(lat, lng, s.lat(), s.lng()),
                        s.h3Cell(),
                        s.updatedAt()
                ))

                /*
                 * Najbliżsi kierowcy na początku.
                 * To nadal dystans po prostej, nie ETA po drogach.
                 */
                .sorted(Comparator.comparingDouble(NearbyDriver::distanceKm))
                .limit(limit)
                .toList();
    }

    /**
     * Odczytuje ostatni snapshot lokalizacji kierowcy z Redis Hash.
     *
     * Jeśli hash nie istnieje, jest pusty albo dane są uszkodzone,
     * zwracamy Optional.empty().
     *
     * Dzięki temu nearby() może po prostu odfiltrować niepoprawne rekordy.
     */
    private Optional<DriverLocationSnapshot> read(String driverId) {
        var raw = redis.opsForHash().entries(
                LocationKeys.driver(driverId)
        );

        if (raw.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DriverLocationSnapshot(
                    raw.get("driverId").toString(),
                    raw.get("cityId").toString(),
                    Double.parseDouble(raw.get("lat").toString()),
                    Double.parseDouble(raw.get("lng").toString()),
                    Double.parseDouble(raw.get("heading").toString()),
                    Double.parseDouble(raw.get("speed").toString()),
                    Double.parseDouble(raw.get("accuracyMeters").toString()),
                    raw.get("h3Cell").toString(),
                    Instant.parse(raw.get("updatedAt").toString())
            ));
        } catch (RuntimeException ex) {
            /*
             * Uszkodzony albo niekompletny hash nie powinien wywalić całego nearby search.
             * Pomijamy takiego kierowcę.
             */
            return Optional.empty();
        }
    }

    /**
     * Liczy dystans w kilometrach między dwoma punktami geograficznymi.
     *
     * Haversine daje dystans po powierzchni Ziemi, czyli "w linii prostej".
     * Dla matchingu to szybki przybliżony ranking kandydatów.
     *
     * Produkcyjnie finalne ETA powinno pochodzić z routing engine / maps service,
     * bo dystans drogowy może mocno różnić się od dystansu geograficznego.
     */
    private static double haversineKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        double r = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        return 2 * r * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );
    }
}