package com.ridesharing.mvp.matching;

import com.ridesharing.mvp.driver.DriverService;
import com.ridesharing.mvp.location.LocationService;
import com.ridesharing.mvp.ride.Ride;
import com.ridesharing.mvp.ride.RideService;
import com.ridesharing.mvp.websocket.RideWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za proste dopasowanie kierowcy do przejazdu.
 *
 * W MVP matching działa bardzo prosto:
 * - szuka dostępnych kierowców blisko punktu odbioru,
 * - przechodzi po kandydatach od najbliższego,
 * - próbuje zarezerwować pierwszego dostępnego kierowcę,
 * - przypisuje go do przejazdu,
 * - jeżeli nikogo nie znajdzie, oznacza przejazd jako nieudany.
 *
 * To nie jest jeszcze zaawansowany matching jak w Uber/Bolt.
 * Nie ma tutaj ETA rankingu, scoringu ML, acceptance probability ani typów pojazdów.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    /**
     * Serwis lokalizacji.
     *
     * Dostarcza listę najbliższych dostępnych kierowców na podstawie Redis GEO.
     * MatchingService nie liczy geolokalizacji samodzielnie — korzysta z LocationService.
     */
    private final LocationService locationService;

    /**
     * Serwis kierowców.
     *
     * Używany tutaj głównie do atomowego zarezerwowania kierowcy przez tryOfferDriver().
     * To zabezpiecza przed przypisaniem tego samego kierowcy do dwóch przejazdów naraz.
     */
    private final DriverService driverService;

    /**
     * Publisher WebSocket dla aktualizacji przejazdu.
     *
     * W tej klasie pole nie jest aktualnie używane.
     * Prawdopodobnie zostało przygotowane pod wysyłanie eventów do pasażera/kierowcy
     * bezpośrednio z matchingu.
     *
     * Jeśli publikacja statusu odbywa się już w RideService.offerDriver(),
     * to to pole można usunąć z MatchingService.
     */
    private final RideWebSocketPublisher publisher;

    /**
     * Początkowy promień wyszukiwania kierowców w kilometrach.
     *
     * Domyślnie 3 km.
     * W MVP system szuka tylko raz w tym promieniu.
     * Produkcyjnie można rozszerzać promień stopniowo, np. 2 km -> 5 km -> 10 km.
     */
    @Value("${app.matching.initial-radius-km:3}")
    private double radiusKm;

    /**
     * Maksymalna liczba kandydatów pobieranych z LocationService.
     *
     * Limit jest ważny, żeby matching nie analizował zbyt wielu kierowców naraz.
     * W MVP bierzemy najbliższych kandydatów i próbujemy przypisać pierwszego możliwego.
     */
    @Value("${app.matching.max-candidates:10}")
    private int maxCandidates;

    /**
     * Uruchamia matching asynchronicznie.
     *
     * @Async powoduje, że request pasażera nie musi czekać synchronicznie
     * na cały proces szukania kierowcy. Ride może zostać utworzony np. w statusie MATCHING,
     * a wynik dopasowania przyjdzie później przez WebSocket albo polling.
     *
     * Flow:
     * 1. Pobiera najbliższych dostępnych kierowców z LocationService.
     * 2. Iteruje po kandydatach.
     * 3. Dla każdego kandydata próbuje go zarezerwować przez DriverService.tryOfferDriver().
     * 4. Pierwszy skutecznie zarezerwowany kierowca dostaje ofertę/przypisanie do ride.
     * 5. Jeżeli żaden kierowca nie jest dostępny, przejazd kończy się jako failed.
     */
    @Async
    public void matchAsync(Ride ride, RideService rideService) {
        /*
         * Pobieramy kandydatów z Redis GEO.
         * Lista powinna być posortowana od najbliższego kierowcy.
         */
        var candidates = locationService.findNearbyAvailableDrivers(
                ride.getPickupLat(),
                ride.getPickupLng(),
                radiusKm,
                maxCandidates
        );

        /*
         * Próbujemy zarezerwować kolejnych kierowców.
         *
         * tryOfferDriver() powinno być atomowe, najlepiej z blokadą pesymistyczną w bazie.
         * To zabezpiecza system przed race condition, gdy dwa przejazdy próbują użyć
         * tego samego kierowcy w tym samym czasie.
         */
        for (var driverId : candidates) {
            if (driverService.tryOfferDriver(driverId)) {
                /*
                 * Po skutecznej rezerwacji kierowcy przekazujemy decyzję do RideService.
                 *
                 * RideService powinien być właścicielem zmiany statusu przejazdu,
                 * np. MATCHING -> DRIVER_ASSIGNED / DRIVER_OFFERED.
                 * Tam powinien też powstać wpis historii statusu i event outbox.
                 */
                rideService.offerDriver(ride.getId(), driverId);
                return;
            }
        }

        /*
         * Brak dostępnych kierowców kończy proces matchingu niepowodzeniem.
         *
         * W MVP to akceptowalne.
         * Produkcyjnie lepiej:
         * - rozszerzać promień,
         * - ponawiać matching przez kilka/kilkanaście sekund,
         * - uwzględnić kierowców, którzy zaraz kończą kurs,
         * - poinformować pasażera o dłuższym oczekiwaniu.
         */
        rideService.markFailed(ride.getId(), "No available drivers nearby");
    }
}