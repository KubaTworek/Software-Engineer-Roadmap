package pl.jakubtworek.backend.reservation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.backend.reservation.api.CreateReservationRequest;
import pl.jakubtworek.backend.reservation.api.ReservationResponse;
import pl.jakubtworek.backend.reservation.client.CatalogClient;
import pl.jakubtworek.backend.reservation.domain.ReservationEntity;
import pl.jakubtworek.backend.reservation.repository.ReservationRepository;
import pl.jakubtworek.backend.reservation.repository.EventInventoryRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import pl.jakubtworek.backend.reservation.api.InventoryCapacityResponse;

/**
 * Warstwa aplikacyjna Reservation Service.
 *
 * Ten serwis odpowiada za przypadki użycia związane z rezerwacjami:
 *
 * - utworzenie rezerwacji,
 * - pobranie rezerwacji,
 * - potwierdzenie rezerwacji,
 * - anulowanie rezerwacji.
 *
 * Reservation Service jest właścicielem stanu rezerwacji. Nie powinien bezpośrednio
 * modyfikować bazy danych Catalog Service ani Order Service.
 *
 * Catalog Service odpowiada za opis wydarzenia, natomiast autorytatywne inventory należy
 * do tego serwisu. Warunkowy UPDATE sprawia, że poprawność nie zależy od czasu odpowiedzi
 * katalogu ani od liczby replik aplikacji.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /**
     * Repozytorium rezerwacji.
     *
     * Reservation Service posiada własną bazę danych i przechowuje w niej stan rezerwacji.
     * To jest zgodne z zasadą database-per-service.
     */
    private final ReservationRepository repository;

    /**
     * Klient HTTP do Catalog Service.
     *
     * Używany do sprawdzenia, czy dla danego wydarzenia istnieje wystarczająca liczba
     * dostępnych biletów.
     *
     * CatalogClient powinien mieć mechanizmy resilience:
     *
     * - timeout,
     * - retry/backoff,
     * - circuit breaker,
     * - fallback.
     */
    private final CatalogClient catalogClient;
    private final EventInventoryRepository inventoryRepository;
    private final Clock clock;

    public ReservationService(ReservationRepository repository, CatalogClient catalogClient,
                              EventInventoryRepository inventoryRepository, Clock clock) {
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    /**
     * Tworzy nową rezerwację.
     *
     * Flow:
     *
     * 1. Pobierz availability z Catalog Service.
     * 2. Sprawdź, czy odpowiedź nie jest pusta.
     * 3. Atomowo spróbuj odjąć bilety w lokalnej bazie właściciela inventory.
     * 4. Utwórz rezerwację w stanie PENDING.
     * 5. Zapisz rezerwację w lokalnej bazie Reservation Service.
     * 6. Zwróć DTO odpowiedzi.
     *
     * Metoda jest transakcyjna, bo zapisuje nową ReservationEntity.
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        /*
         * Sprawdzenie dostępności odbywa się przez Catalog Service.
         *
         * To jest synchroniczna zależność service-to-service. Jeśli Catalog Service jest
         * niedostępny, CatalogClient powinien rzucić kontrolowany wyjątek, a request
         * rezerwacji powinien zakończyć się błędem zamiast tworzyć rezerwację "w ciemno".
         */
        CatalogClient.AvailabilityResponse availability = catalogClient.getAvailability(request.eventId());

        /*
         * Defensive check.
         *
         * Normalnie klient HTTP powinien zwrócić poprawny obiekt albo rzucić wyjątek.
         * Jeśli jednak dostaniemy null, traktujemy to jako błąd zależności downstream.
         */
        if (availability == null) {
            throw new IllegalStateException("Catalog service returned empty availability response");
        }

        /*
         * Catalog dostarcza metadane startowe, ale od pierwszego zapisu jedynym właścicielem
         * licznika jest Reservation Service. Ponowne wywołanie nie resetuje sprzedanych miejsc.
         * W większym systemie ten bootstrap byłby konsumentem EventCreated + reconciliation;
         * tu ten sam kontrakt pokazujemy bez dokładania drugiego relaya outbox do laboratorium.
         */
        inventoryRepository.initializeIfAbsent(availability.eventId(), availability.totalTickets());

        /*
         * Walidacja biznesowa: nie tworzymy rezerwacji, jeśli brakuje biletów.
         *
         * Warto zauważyć, że to sprawdzenie samo w sobie nie jest wystarczające przy dużej
         * równoległości, bo wiele requestów może jednocześnie zobaczyć tę samą availability.
         */
        /* Catalog potwierdza istnienie, lecz jego licznik może być opóźnionym read modelem. */
        if (inventoryRepository.reserve(request.eventId(), request.quantity()) != 1) {
            throw new IllegalArgumentException("Not enough tickets available for event " + request.eventId());
        }

        /*
         * Tworzymy rezerwację w stanie PENDING.
         *
         * PENDING oznacza, że użytkownik tymczasowo zarezerwował bilety, ale płatność
         * nie została jeszcze zakończona.
         */
        ReservationEntity reservation = ReservationEntity.pending(
                request.eventId(),
                request.userId(),
                request.quantity(),
                clock.instant()
        );

        log.info("reservation_created eventId={} userId={} quantity={}",
                request.eventId(), request.userId(), request.quantity());

        return toResponse(repository.save(reservation));
    }

    /**
     * Pobiera rezerwację po ID.
     *
     * Jeśli rezerwacja wygasła, metoda rzuca wyjątek zamiast zwracać ją jako poprawną.
     *
     * Uwaga:
     * Obecnie metoda tylko wykrywa wygaśnięcie, ale nie zapisuje statusu EXPIRED w bazie.
     * Jeśli chcesz utrwalać status wygaśnięcia przy odczycie, metoda nie powinna być readOnly.
     */
    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        /*
         * Rezerwacja ma ograniczony czas życia.
         *
         * Jeśli użytkownik nie opłaci jej przed expiresAt, nie powinna być dalej traktowana
         * jako aktywna.
         */
        if (reservation.isExpired(clock.instant())) {
            throw new IllegalStateException("Reservation expired: " + id);
        }

        return toResponse(reservation);
    }

    /**
     * Potwierdza rezerwację.
     *
     * Ten endpoint jest zwykle wywoływany przez Order Service po udanej płatności.
     *
     * Flow:
     *
     * 1. Pobierz rezerwację.
     * 2. Sprawdź, czy nie wygasła.
     * 3. Jeśli wygasła, oznacz ją jako EXPIRED i rzuć błąd.
     * 4. Jeśli jest aktywna, oznacz ją jako CONFIRMED.
     * 5. Zwróć aktualny stan rezerwacji.
     */
    @Transactional
    public ReservationResponse confirm(UUID id) {
        ReservationEntity reservation = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        /*
         * Nie potwierdzamy wygasłych rezerwacji.
         *
         * Jeśli rezerwacja wygasła, zapisujemy ten fakt w encji i przerywamy flow.
         */
        Instant now = clock.instant();
        if (reservation.isExpired(now)) {
            if (reservation.expire(now)) {
                releaseInventory(reservation);
            }

            throw new IllegalStateException("Reservation expired: " + id);
        }

        /*
         * Zmiana statusu na CONFIRMED.
         *
         * ReservationEntity powinna pilnować dozwolonych przejść stanów, np.:
         *
         * PENDING -> CONFIRMED
         *
         * i odrzucać niedozwolone przejścia, np.:
         *
         * CANCELLED -> CONFIRMED
         */
        reservation.confirm(now);

        log.info("reservation_confirmed reservationId={}", id);

        return toResponse(reservation);
    }

    /**
     * Anuluje rezerwację.
     *
     * Może być używane przez użytkownika, system wygaszeń albo operację kompensacyjną.
     *
     * ReservationEntity powinna sprawdzić, czy dany status pozwala na anulowanie.
     */
    @Transactional
    public ReservationResponse cancel(UUID id) {
        ReservationEntity reservation = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        if (reservation.cancel(clock.instant())) {
            releaseInventory(reservation);
        }

        log.info("reservation_cancelled reservationId={}", id);

        return toResponse(reservation);
    }

    /**
     * Wygasza batch zdobyty przez SKIP LOCKED. Jeśli worker A obudzi się po utracie lease,
     * jego starszy fencing token nie zostanie zaakceptowany przez inventory i transakcja
     * wycofa również zmianę statusu rezerwacji.
     */
    @Transactional
    public void expireBatch(long fencingToken, Instant now) {
        for (ReservationEntity reservation : repository.lockExpiredBatch(now)) {
            if (reservation.expire(now)
                    && inventoryRepository.releaseWithFencing(reservation.getEventId(),
                    reservation.getQuantity(), fencingToken) != 1) {
                throw new IllegalStateException("Inventory rejected fencing token " + fencingToken
                        + " or releasing the reservation would exceed the configured capacity");
            }
        }
    }

    /** Wersjonowana komenda administracyjna; retry starego requestu nie nadpisze nowszej decyzji. */
    @Transactional
    public InventoryCapacityResponse changeCapacity(UUID eventId, long expectedVersion, int newTotal) {
        if (inventoryRepository.changeCapacity(eventId, expectedVersion, newTotal) != 1) {
            throw new IllegalStateException("Capacity version is stale or new total is below already reserved tickets");
        }
        var inventory = inventoryRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory not found: " + eventId));
        return new InventoryCapacityResponse(inventory.getEventId(), inventory.getTotalTickets(),
                inventory.getAvailableTickets(), inventory.getVersion());
    }

    /**
     * Mapuje encję rezerwacji na DTO odpowiedzi API.
     *
     * Nie zwracamy ReservationEntity bezpośrednio z kontrolera, żeby nie mieszać
     * modelu persistence z kontraktem HTTP.
     */
    private ReservationResponse toResponse(ReservationEntity reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private void releaseInventory(ReservationEntity reservation) {
        if (inventoryRepository.release(reservation.getEventId(), reservation.getQuantity()) != 1) {
            throw new IllegalStateException("Inventory invariant violated for event " + reservation.getEventId());
        }
    }
}
