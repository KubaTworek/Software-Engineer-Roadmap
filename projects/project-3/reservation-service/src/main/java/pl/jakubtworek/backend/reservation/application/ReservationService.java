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

import java.time.Instant;
import java.util.UUID;

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
 * Uwaga architektoniczna:
 * W tej wersji create(...) sprawdza availability przez Catalog Service, ale nie zmniejsza
 * faktycznie puli dostępnych biletów w Catalog Service. To oznacza, że przy dużej równoległości
 * możliwy jest overselling. Produkcyjnie trzeba byłoby dodać atomową rezerwację inventory,
 * np. przez osobny Inventory Service, transakcję z lockiem, optimistic locking albo event-driven
 * mechanizm kompensacji.
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

    public ReservationService(ReservationRepository repository, CatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    /**
     * Tworzy nową rezerwację.
     *
     * Flow:
     *
     * 1. Pobierz availability z Catalog Service.
     * 2. Sprawdź, czy odpowiedź nie jest pusta.
     * 3. Sprawdź, czy jest wystarczająca liczba biletów.
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
         * Walidacja biznesowa: nie tworzymy rezerwacji, jeśli brakuje biletów.
         *
         * Warto zauważyć, że to sprawdzenie samo w sobie nie jest wystarczające przy dużej
         * równoległości, bo wiele requestów może jednocześnie zobaczyć tę samą availability.
         */
        if (availability.availableTickets() < request.quantity()) {
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
                request.quantity()
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
        if (reservation.isExpired(Instant.now())) {
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
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        /*
         * Nie potwierdzamy wygasłych rezerwacji.
         *
         * Jeśli rezerwacja wygasła, zapisujemy ten fakt w encji i przerywamy flow.
         */
        if (reservation.isExpired(Instant.now())) {
            reservation.expire();

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
        reservation.confirm();

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
        ReservationEntity reservation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + id));

        reservation.cancel();

        log.info("reservation_cancelled reservationId={}", id);

        return toResponse(reservation);
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
}