package com.ridesharing.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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

    private static final DefaultRedisScript<Long> UPDATE_LOCATION =
            new DefaultRedisScript<>("""
                    local current = redis.call('HGET', KEYS[1], 'sequence')
                    if current and tonumber(current) >= tonumber(ARGV[1]) then
                      return 0
                    end
                    local oldCity = redis.call('HGET', KEYS[1], 'cityId')
                    local oldCell = redis.call('HGET', KEYS[1], 'h3Cell')
                    if oldCity and oldCell and (oldCity ~= ARGV[2] or oldCell ~= ARGV[3]) then
                      redis.call('SREM', 'loc:city:' .. oldCity .. ':h3:' .. oldCell, ARGV[4])
                    end
                    if oldCity and oldCity ~= ARGV[2] then
                      redis.call('ZREM', 'loc:city:' .. oldCity .. ':available', ARGV[4])
                    end
                    redis.call('HSET', KEYS[1],
                      'driverId', ARGV[4], 'cityId', ARGV[2], 'h3Cell', ARGV[3],
                      'lat', ARGV[5], 'lng', ARGV[6], 'heading', ARGV[7],
                      'speed', ARGV[8], 'accuracyMeters', ARGV[9],
                      'sequence', ARGV[1], 'recordedAt', ARGV[10], 'receivedAt', ARGV[11])
                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[12]))
                    redis.call('SADD', KEYS[2], ARGV[4])
                    redis.call('EXPIRE', KEYS[2], 600)
                    redis.call('GEOADD', KEYS[3], ARGV[6], ARGV[5], ARGV[4])
                    return 1
                    """, Long.class);

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
        Instant receivedAt = Instant.now();
        var snapshot = new DriverLocationSnapshot(
                request.driverId(),
                request.cityId(),
                request.lat(),
                request.lng(),
                request.heading() == null ? 0 : request.heading(),
                request.speed() == null ? 0 : request.speed(),
                request.accuracyMeters() == null ? 0 : request.accuracyMeters(),
                cell,
                request.sequence(),
                request.recordedAt(),
                receivedAt
        );

        /*
         * Redis Hash z ostatnim snapshotem kierowcy.
         *
         * Klucz prawdopodobnie ma format typu:
         * driver:location:{driverId}
         *
         * Hash pozwala szybko odczytać wszystkie pola ostatniej lokalizacji.
         */
        Long applied = redis.execute(
                UPDATE_LOCATION,
                List.of(
                        LocationKeys.driver(snapshot.driverId()),
                        LocationKeys.cell(snapshot.cityId(), snapshot.h3Cell()),
                        LocationKeys.available(snapshot.cityId())
                ),
                Long.toString(snapshot.sequence()),
                snapshot.cityId(),
                snapshot.h3Cell(),
                snapshot.driverId(),
                Double.toString(snapshot.lat()),
                Double.toString(snapshot.lng()),
                Double.toString(snapshot.heading()),
                Double.toString(snapshot.speed()),
                Double.toString(snapshot.accuracyMeters()),
                snapshot.recordedAt().toString(),
                snapshot.receivedAt().toString(),
                Long.toString(staleAfterSeconds * 4L)
        );

        if (applied == null || applied == 0) {
            return read(snapshot.driverId()).orElse(snapshot);
        }

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
                        .between(s.receivedAt(), Instant.now())
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
                        s.receivedAt()
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
     * Usuwa snapshot, członkostwo H3 i wpis GEO. Najpierw odczytuje ostatni
     * snapshot, ponieważ to on wskazuje dokładny city/cell owning the membership.
     */
    public void remove(String driverId) {
        read(driverId).ifPresent(snapshot -> {
            redis.opsForSet().remove(LocationKeys.cell(snapshot.cityId(), snapshot.h3Cell()), driverId);
            redis.opsForGeo().remove(LocationKeys.available(snapshot.cityId()), driverId);
        });
        redis.delete(LocationKeys.driver(driverId));
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
                    Long.parseLong(raw.get("sequence").toString()),
                    Instant.parse(raw.get("recordedAt").toString()),
                    Instant.parse(raw.get("receivedAt").toString())
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
