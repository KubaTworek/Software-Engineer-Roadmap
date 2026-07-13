package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.driver.Driver;
import com.ridesharing.mvp.driver.DriverService;
import com.ridesharing.mvp.maps.MapsClient;
import com.ridesharing.mvp.matching.MatchingService;
import com.ridesharing.mvp.outbox.OutboxService;
import com.ridesharing.mvp.payment.PaymentService;
import com.ridesharing.mvp.user.AppUser;
import com.ridesharing.mvp.websocket.RideWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Główny serwis domenowy przejazdów.
 *
 * To jedna z najważniejszych klas w aplikacji ride-sharing.
 * RideService jest właścicielem cyklu życia przejazdu:
 *
 * REQUESTED -> MATCHING -> DRIVER_ASSIGNED -> DRIVER_ARRIVING
 * -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
 *
 * Odpowiada też za skutki uboczne zmian statusu:
 * - autoryzację i capture płatności,
 * - uruchomienie matchingu,
 * - zmianę statusu kierowcy,
 * - publikację eventów WebSocket,
 * - zapis eventów outbox do późniejszej publikacji przez Kafkę,
 * - zapis historii statusów.
 *
 * Controller nie powinien omijać tej klasy przy zmianach przejazdu,
 * bo wtedy łatwo zgubić historię, eventy albo reguły state machine.
 */
@Service
@RequiredArgsConstructor
public class RideService {

    /**
     * Repozytorium przejazdów.
     *
     * Służy do zapisu i odczytu głównego agregatu Ride.
     * Operacje zmieniające stan używają locked(), czyli pobrania z blokadą,
     * żeby ograniczyć race condition między np. anulowaniem, akceptacją i zakończeniem kursu.
     */
    private final RideRepository rides;

    /**
     * Repozytorium historii statusów.
     *
     * Każda istotna zmiana statusu przejazdu powinna być zapisana w tej tabeli.
     * To jest kluczowe dla supportu, audytu i debugowania sporów pasażer-kierowca.
     */
    private final RideStatusHistoryRepository history;

    /**
     * State machine przejazdu.
     *
     * Waliduje, czy przejście z jednego statusu do drugiego jest dozwolone.
     * Dzięki temu np. COMPLETED nie wróci przypadkowo do MATCHING.
     */
    private final RideStateMachine stateMachine;

    /**
     * Klient map / routingu.
     *
     * Używany do oszacowania dystansu i czasu przejazdu.
     * W MVP może to być mock, a produkcyjnie Google Maps, Mapbox, OSRM albo inny provider.
     */
    private final MapsClient mapsClient;

    /**
     * Lokalny serwis pricingu.
     *
     * Liczy cenę orientacyjną na podstawie dystansu i czasu.
     * W późniejszych etapach może zostać zastąpiony osobnym Pricing Service.
     */
    private final PricingService pricingService;

    /**
     * Serwis płatności.
     *
     * W requestRide() autoryzuje płatność.
     * W completeRide() wykonuje capture.
     *
     * To ważne rozdzielenie:
     * - authorize: sprawdza/blokuje środki przed kursem,
     * - capture: pobiera finalną kwotę po zakończeniu kursu.
     */
    private final PaymentService paymentService;

    /**
     * Serwis kierowców.
     *
     * RideService używa go do:
     * - pobrania kierowcy,
     * - ustawienia kierowcy jako ON_TRIP,
     * - przywrócenia kierowcy do AVAILABLE po anulowaniu, odrzuceniu lub zakończeniu kursu.
     */
    private final DriverService driverService;

    /**
     * Publisher WebSocket.
     *
     * Wysyła bieżące aktualizacje przejazdu do aplikacji pasażera/kierowcy.
     * To zapewnia real-time feedback bez konieczności ciągłego pollingu.
     */
    private final RideWebSocketPublisher publisher;

    /**
     * Serwis outbox.
     *
     * Zapisuje event domenowy w bazie.
     * Później OutboxPublisher opublikuje go do Kafki.
     *
     * Dzięki temu zmiana statusu przejazdu i event są spójne transakcyjnie.
     */
    private final OutboxService outbox;

    /**
     * MatchingService uruchamia szukanie kierowcy.
     *
     * @Lazy najpewniej służy do przerwania cyklicznej zależności:
     * RideService potrzebuje MatchingService, a MatchingService woła z powrotem RideService.
     *
     * Działa, ale architektonicznie lepszy byłby event:
     * RideRequested -> Kafka/outbox -> Matching consumer.
     */
    @Lazy
    private final MatchingService matchingService;

    /**
     * Liczy orientacyjną cenę przejazdu bez tworzenia przejazdu.
     *
     * Flow:
     * 1. MapsClient liczy dystans i czas.
     * 2. PricingService liczy cenę.
     * 3. API zwraca estimate do aplikacji pasażera.
     *
     * To jest tylko wycena informacyjna.
     * Nie rezerwuje kierowcy i nie autoryzuje płatności.
     */
    public RideController.EstimateResponse estimate(RideController.EstimateRequest request) {
        var route = mapsClient.estimateRoute(
                request.pickup().lat(),
                request.pickup().lng(),
                request.dropoff().lat(),
                request.dropoff().lng()
        );

        var price = pricingService.estimatePrice(
                route.distanceKm(),
                route.durationMinutes()
        );

        return new RideController.EstimateResponse(
                price,
                "PLN",
                route.distanceKm(),
                route.durationMinutes()
        );
    }

    /**
     * Tworzy nowy przejazd dla pasażera.
     *
     * To główny flow zamawiania kursu.
     *
     * Flow:
     * 1. Liczy trasę i cenę.
     * 2. Tworzy Ride w statusie REQUESTED.
     * 3. Zapisuje status początkowy do historii.
     * 4. Przechodzi do MATCHING.
     * 5. Autoryzuje płatność.
     * 6. Publikuje event RideRequested.
     * 7. Uruchamia asynchroniczny matching.
     *
     * Metoda jest transakcyjna, więc zapis przejazdu, historia i outbox event
     * są częścią jednej operacji domenowej.
     */
    @Transactional
    public RideDto requestRide(AppUser passenger, RideController.RideRequest request) {
        var route = mapsClient.estimateRoute(
                request.pickup().lat(),
                request.pickup().lng(),
                request.dropoff().lat(),
                request.dropoff().lng()
        );

        var price = pricingService.estimatePrice(
                route.distanceKm(),
                route.durationMinutes()
        );

        var now = Instant.now();

        var ride = Ride.builder()
                .id(UUID.randomUUID())
                .passenger(passenger)
                .status(RideStatus.REQUESTED)
                .pickupLat(request.pickup().lat())
                .pickupLng(request.pickup().lng())
                .pickupAddress(request.pickup().address())
                .dropoffLat(request.dropoff().lat())
                .dropoffLng(request.dropoff().lng())
                .dropoffAddress(request.dropoff().address())
                .estimatedDistanceKm(BigDecimal.valueOf(route.distanceKm()))
                .estimatedDurationMinutes(route.durationMinutes())
                .estimatedPrice(price)
                .currency("PLN")
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        rides.save(ride);

        /*
         * Pierwszy wpis historii ma previousStatus = null.
         * Dzięki temu pełna historia przejazdu zaczyna się od REQUESTED.
         */
        recordInitialStatus(ride, passenger.getId(), "ride requested");

        /*
         * Po utworzeniu przejazdu system od razu przechodzi do MATCHING.
         * State machine waliduje, czy REQUESTED -> MATCHING jest dozwolone.
         */
        transition(ride, RideStatus.MATCHING, "SYSTEM", null, "matching started");

        /*
         * Autoryzacja płatności odbywa się przed przypisaniem/przed startem kursu.
         * To ogranicza ryzyko wykonania kursu bez możliwości pobrania płatności.
         */
        paymentService.authorize(ride);

        /*
         * Event idzie dwutorowo:
         * - WebSocket: szybka aktualizacja UI,
         * - Outbox: trwały event do Kafki.
         */
        emit(ride, "RideRequested", "Ride requested; matching started");

        /*
         * Matching jest asynchroniczny.
         * Pasażer dostaje odpowiedź z aktualnym statusem, a znalezienie kierowcy
         * przyjdzie później przez WebSocket/polling.
         */
        matchingService.matchAsync(ride, this);

        return RideDto.from(ride);
    }

    /**
     * Pobiera przejazd po ID.
     *
     * Uwaga: ta metoda sama nie sprawdza, czy aktualny użytkownik ma prawo zobaczyć ride.
     * Kontrola dostępu musi być wykonana w controllerze albo tutaj przez wersję metody
     * przyjmującą AppUser.
     */
    public RideDto get(UUID rideId) {
        return RideDto.from(find(rideId));
    }

    /**
     * Zwraca ostatnie przejazdy, opcjonalnie filtrowane po statusie.
     *
     * Używane głównie przez panel admina/support.
     * Limit 100 jest rozsądny dla MVP, ale produkcyjnie potrzebna jest paginacja.
     */
    public List<RideDto> listRecent(RideStatus status) {
        var rows = status == null
                ? rides.findTop100ByOrderByRequestedAtDesc()
                : rides.findTop100ByStatusOrderByRequestedAtDesc(status);

        return rows.stream()
                .map(RideDto::from)
                .toList();
    }

    /**
     * Zwraca pełną historię statusów przejazdu.
     *
     * To kluczowe dla supportu:
     * można sprawdzić, kto i kiedy zmienił status oraz z jakiego powodu.
     */
    public List<RideStatusHistory> history(UUID rideId) {
        return history.findByRideIdOrderByCreatedAtAsc(rideId);
    }

    /**
     * Przypisuje kierowcę znalezionego przez MatchingService.
     *
     * Ta metoda jest wołana po tym, jak MatchingService zarezerwował kierowcę
     * przez DriverService.tryOfferDriver().
     *
     * Jeżeli przejazd nie jest już w MATCHING, kierowca jest zwalniany.
     * To chroni przed race condition, np. gdy pasażer anulował przejazd
     * dokładnie w trakcie matchingu.
     */
    @Transactional
    public void offerDriver(UUID rideId, UUID driverId) {
        var ride = locked(rideId);

        if (ride.getStatus() != RideStatus.MATCHING) {
            driverService.markAvailable(driverId);
            return;
        }

        var driver = driverService.getById(driverId);

        ride.setDriver(driver);
        ride.setAcceptedAt(Instant.now());

        /*
         * W tej implementacji DRIVER_ASSIGNED oznacza przypisanie/ofertę kierowcy.
         * Komunikat mówi, że system czeka jeszcze na explicit driver acceptance.
         */
        transition(
                ride,
                RideStatus.DRIVER_ASSIGNED,
                "SYSTEM",
                driverId,
                "driver offered and assigned"
        );

        rides.save(ride);

        emit(
                ride,
                "DriverAssigned",
                "Driver assigned. Waiting for explicit driver acceptance."
        );
    }

    /**
     * Kierowca akceptuje przypisany przejazd.
     *
     * Flow:
     * 1. Blokuje ride.
     * 2. Sprawdza, czy ten kierowca jest przypisany do tego przejazdu.
     * 3. Wymaga statusu DRIVER_ASSIGNED.
     * 4. Zmienia status na DRIVER_ARRIVING.
     * 5. Oznacza kierowcę jako ON_TRIP.
     * 6. Publikuje event.
     *
     * To finalizuje etap matchingu i zaczyna fazę dojazdu po pasażera.
     */
    @Transactional
    public RideDto acceptRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);

        assertDriverAssigned(ride, driver);
        requireStatus(ride, RideStatus.DRIVER_ASSIGNED);

        transition(
                ride,
                RideStatus.DRIVER_ARRIVING,
                "DRIVER",
                driver.getId(),
                "driver accepted"
        );

        driverService.markOnTrip(driver.getId());

        rides.save(ride);

        emit(ride, "DriverAcceptedRide", "Driver accepted and is arriving");

        return RideDto.from(ride);
    }

    /**
     * Kierowca odrzuca przypisany przejazd.
     *
     * Flow:
     * 1. Sprawdza, czy kierowca faktycznie jest przypisany.
     * 2. Usuwa kierowcę z ride.
     * 3. Wraca do statusu MATCHING.
     * 4. Przywraca kierowcę do AVAILABLE.
     * 5. Publikuje event.
     * 6. Uruchamia matching ponownie.
     *
     * To obsługuje sytuację, gdy pierwszy kandydat nie chce kursu.
     */
    @Transactional
    public RideDto rejectRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);

        assertDriverAssigned(ride, driver);

        ride.setDriver(null);

        transition(
                ride,
                RideStatus.MATCHING,
                "DRIVER",
                driver.getId(),
                "driver rejected; rematching"
        );

        driverService.markAvailable(driver.getId());

        rides.save(ride);

        emit(ride, "DriverRejectedRide", "Driver rejected; matching restarted");

        matchingService.matchAsync(ride, this);

        return RideDto.from(ride);
    }

    /**
     * Kierowca oznacza, że dotarł do miejsca odbioru.
     *
     * Ten status ma znaczenie operacyjne:
     * - pasażer widzi, że kierowca czeka,
     * - support może rozstrzygać spory,
     * - w przyszłości można naliczać opłatę za oczekiwanie/no-show.
     */
    @Transactional
    public RideDto markArrived(Driver driver, UUID rideId) {
        var ride = locked(rideId);

        assertDriverAssigned(ride, driver);
        requireStatus(ride, RideStatus.DRIVER_ARRIVING);

        ride.setDriverArrivedAt(Instant.now());

        transition(
                ride,
                RideStatus.DRIVER_ARRIVED,
                "DRIVER",
                driver.getId(),
                "driver arrived"
        );

        rides.save(ride);

        emit(ride, "DriverArrived", "Driver arrived");

        return RideDto.from(ride);
    }

    /**
     * Kierowca rozpoczyna kurs.
     *
     * Dozwolone są dwa statusy startowe:
     * - DRIVER_ARRIVED,
     * - DRIVER_ARRIVING.
     *
     * To daje elastyczność MVP: kierowca może rozpocząć kurs nawet bez osobnego kliknięcia ARRIVED.
     * Produkcyjnie zwykle lepiej pilnować dokładniejszego flow.
     */
    @Transactional
    public RideDto startRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);

        assertDriverAssigned(ride, driver);

        if (ride.getStatus() != RideStatus.DRIVER_ARRIVED
                && ride.getStatus() != RideStatus.DRIVER_ARRIVING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Ride cannot be started from status " + ride.getStatus()
            );
        }

        ride.setStartedAt(Instant.now());

        transition(
                ride,
                RideStatus.IN_PROGRESS,
                "DRIVER",
                driver.getId(),
                "ride started"
        );

        rides.save(ride);

        emit(ride, "RideStarted", "Ride started");

        return RideDto.from(ride);
    }

    /**
     * Kierowca kończy przejazd.
     *
     * To krytyczna operacja, bo powoduje kilka skutków ubocznych:
     * - status przejazdu zmienia się na COMPLETED,
     * - finalPrice zostaje ustawiony,
     * - uruchamiany jest capture płatności,
     * - kierowca wraca do AVAILABLE,
     * - publikowany jest event.
     *
     * Metoda wymaga statusu IN_PROGRESS, żeby nie pobrać płatności
     * za kurs, który się nie rozpoczął.
     */
    @Transactional
    public RideDto completeRide(Driver driver, UUID rideId) {
        var ride = locked(rideId);

        assertDriverAssigned(ride, driver);
        requireStatus(ride, RideStatus.IN_PROGRESS);

        ride.setCompletedAt(Instant.now());

        /*
         * MVP: finalna cena = cena szacowana.
         * Produkcyjnie finalPrice powinien uwzględniać realny dystans, czas,
         * postoje, opłaty dodatkowe, promocje i ewentualny surge.
         */
        ride.setFinalPrice(ride.getEstimatedPrice());

        transition(
                ride,
                RideStatus.COMPLETED,
                "DRIVER",
                driver.getId(),
                "ride completed"
        );

        rides.save(ride);

        /*
         * Capture płatności jest wykonywany po oznaczeniu przejazdu jako COMPLETED.
         * Jeżeli capture się nie uda, PaymentService powinien oznaczyć płatność
         * jako failed/pending i uruchomić retry/reconciliation.
         */
        paymentService.capture(ride);

        /*
         * Po zakończeniu kursu kierowca wraca do puli dostępnych.
         * Produkcyjnie warto sprawdzić, czy kierowca nie przełączył się na OFFLINE.
         */
        driverService.markAvailable(driver.getId());

        emit(
                ride,
                "RideCompleted",
                "Ride completed and payment capture requested"
        );

        return RideDto.from(ride);
    }

    /**
     * Anuluje przejazd z perspektywy pasażera.
     *
     * Flow:
     * 1. Blokuje ride.
     * 2. Sprawdza, czy pasażer jest właścicielem przejazdu.
     * 3. Odrzuca anulowanie dla COMPLETED i IN_PROGRESS.
     * 4. Ustawia reason i cancelledAt.
     * 5. Zmienia status na CANCELLED_BY_PASSENGER.
     * 6. Jeżeli był przypisany kierowca, przywraca go do AVAILABLE.
     * 7. Publikuje event.
     */
    @Transactional
    public RideDto cancelByPassenger(AppUser passenger, UUID rideId, String reason) {
        var ride = locked(rideId);

        if (!ride.getPassenger().getId().equals(passenger.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not your ride");
        }

        if (ride.getStatus() == RideStatus.COMPLETED
                || ride.getStatus() == RideStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Ride cannot be cancelled from status " + ride.getStatus()
            );
        }

        var driver = ride.getDriver();

        ride.setCancellationReason(reason);
        ride.setCancelledAt(Instant.now());

        transition(
                ride,
                RideStatus.CANCELLED_BY_PASSENGER,
                "PASSENGER",
                passenger.getId(),
                reason
        );

        rides.save(ride);

        /*
         * Jeżeli kierowca był już przypisany, zwalniamy go do kolejnych zleceń.
         */
        if (driver != null) {
            driverService.markAvailable(driver.getId());
        }

        emit(ride, "RideCancelledByPassenger", "Ride cancelled by passenger");

        return RideDto.from(ride);
    }

    /**
     * Awaryjne anulowanie przejazdu przez administratora.
     *
     * To operacja operatorska, używana np. przy problemach bezpieczeństwa,
     * błędach systemu albo interwencji supportu.
     *
     * Zamiast CANCELLED_BY_PASSENGER/DRIVER ustawiany jest FAILED,
     * bo to nie jest standardowe anulowanie przez uczestnika przejazdu.
     */
    @Transactional
    public RideDto forceCancelByAdmin(UUID rideId, AppUser admin, String reason) {
        var ride = locked(rideId);

        if (ride.getStatus() == RideStatus.COMPLETED
                || ride.getStatus() == RideStatus.CANCELLED_BY_PASSENGER
                || ride.getStatus() == RideStatus.CANCELLED_BY_DRIVER) {
            throw new ApiException(HttpStatus.CONFLICT, "Ride is already terminal");
        }

        var driver = ride.getDriver();

        ride.setCancellationReason(reason);
        ride.setCancelledAt(Instant.now());

        transition(
                ride,
                RideStatus.FAILED,
                "ADMIN",
                admin.getId(),
                reason
        );

        rides.save(ride);

        if (driver != null) {
            driverService.markAvailable(driver.getId());
        }

        emit(ride, "RideForceCancelled", "Ride force-cancelled by admin");

        return RideDto.from(ride);
    }

    /**
     * Oznacza przejazd jako failed, najczęściej gdy matching nie znalazł kierowcy.
     *
     * Metoda działa tylko dla statusu MATCHING.
     * Jeżeli przejazd zmienił już status, np. pasażer anulował albo kierowca został przypisany,
     * metoda nic nie robi.
     *
     * To chroni przed opóźnionym wynikiem asynchronicznego matchingu.
     */
    @Transactional
    public void markFailed(UUID rideId, String reason) {
        var ride = locked(rideId);

        if (ride.getStatus() != RideStatus.MATCHING) {
            return;
        }

        ride.setCancellationReason(reason);

        transition(
                ride,
                RideStatus.FAILED,
                "SYSTEM",
                null,
                reason
        );

        rides.save(ride);

        emit(ride, "RideFailed", reason);
    }

    /**
     * Pobiera ride bez blokady.
     *
     * Używane do zwykłych odczytów.
     */
    private Ride find(UUID rideId) {
        return rides.findById(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    /**
     * Pobiera ride z blokadą.
     *
     * Operacje zmieniające status powinny używać tej metody.
     * Blokada ogranicza race condition między równoległymi requestami,
     * np. accept vs cancel albo complete vs repeated complete.
     */
    private Ride locked(UUID rideId) {
        return rides.findWithLockById(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    /**
     * Sprawdza, czy dany kierowca jest przypisany do przejazdu.
     *
     * To podstawowa kontrola bezpieczeństwa po stronie kierowcy.
     * Kierowca nie może akceptować, startować ani kończyć cudzego przejazdu.
     */
    private void assertDriverAssigned(Ride ride, Driver driver) {
        if (ride.getDriver() == null
                || !ride.getDriver().getId().equals(driver.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Driver is not assigned to this ride"
            );
        }
    }

    /**
     * Wymaga konkretnego statusu przejazdu.
     *
     * Używane tam, gdzie operacja ma sens tylko z jednego stanu,
     * np. acceptRide wymaga DRIVER_ASSIGNED,
     * completeRide wymaga IN_PROGRESS.
     */
    private void requireStatus(Ride ride, RideStatus status) {
        if (ride.getStatus() != status) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Expected status " + status + " but was " + ride.getStatus()
            );
        }
    }

    /**
     * Zapisuje pierwszy wpis historii statusów.
     *
     * previousStatus jest null, bo to początek lifecycle’u przejazdu.
     */
    private void recordInitialStatus(Ride ride, UUID actorId, String reason) {
        history.save(RideStatusHistory.builder()
                .id(UUID.randomUUID())
                .ride(ride)
                .previousStatus(null)
                .newStatus(ride.getStatus())
                .actorType("PASSENGER")
                .actorId(actorId)
                .reason(reason)
                .build());
    }

    /**
     * Centralna metoda zmiany statusu przejazdu.
     *
     * Każda zmiana statusu powinna przejść przez tę metodę, bo:
     * - sprawdza state machine,
     * - ustawia nowy status,
     * - zapisuje historię przejścia.
     *
     * To zapobiega sytuacji, gdzie status zmienia się bez audytu.
     */
    private void transition(
            Ride ride,
            RideStatus target,
            String actorType,
            UUID actorId,
            String reason
    ) {
        var previous = ride.getStatus();

        stateMachine.assertTransition(previous, target);

        ride.setStatus(target);

        history.save(RideStatusHistory.builder()
                .id(UUID.randomUUID())
                .ride(ride)
                .previousStatus(previous)
                .newStatus(target)
                .actorType(actorType)
                .actorId(actorId)
                .reason(reason)
                .build());
    }

    /**
     * Publikuje informację o zmianie przejazdu.
     *
     * Robi dwie rzeczy:
     * 1. Wysyła update przez WebSocket do aplikacji klienta.
     * 2. Zapisuje event outbox, który zostanie później wysłany do Kafki.
     *
     * WebSocket daje szybki real-time UX.
     * Outbox/Kafka daje trwały event dla innych komponentów: płatności, fraudu,
     * supportu, analityki, data warehouse albo powiadomień.
     */
    private void emit(Ride ride, String eventType, String message) {
        publisher.publishRideEvent(ride, message);

        outbox.rideEvent(
                ride.getId(),
                eventType,
                Map.of(
                        "rideId", ride.getId().toString(),
                        "status", ride.getStatus().name(),
                        "message", message,
                        "occurredAt", Instant.now().toString()
                )
        );
    }
}