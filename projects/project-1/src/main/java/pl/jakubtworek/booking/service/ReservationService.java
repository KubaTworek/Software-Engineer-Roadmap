package pl.jakubtworek.booking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.cache.AvailabilitySnapshotCache;
import pl.jakubtworek.booking.cache.EventDetailsCache;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.Customer;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;
import pl.jakubtworek.booking.repository.CustomerRepository;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

/**
 * Główny serwis aplikacyjny odpowiedzialny za rezerwacje.
 *
 * To jedna z najważniejszych klas w projekcie, bo łączy kilka krytycznych tematów:
 *
 * - transakcje,
 * - ochronę przed oversellingiem,
 * - zapis rezerwacji,
 * - zmianę statusu rezerwacji,
 * - zwalnianie miejsc przy anulowaniu,
 * - invalidację cache po zmianie danych.
 *
 * Ten serwis jest częścią zwykłego monolitu warstwowego:
 *
 * controller -> service -> repository -> database
 *
 * Nie ma tu osobnych mikroserwisów ani event-driven architektury. PostgreSQL
 * pozostaje źródłem prawdy dla rezerwacji i dostępności.
 */
@Service
public class ReservationService {

    /**
     * Repozytorium eventów.
     *
     * Używane do sprawdzenia, czy event istnieje oraz do pobrania encji Event,
     * która będzie powiązana z nową rezerwacją.
     */
    private final EventRepository eventRepository;

    /**
     * Repozytorium klientów.
     *
     * W tym prostym MVP klient jest wyszukiwany po emailu.
     * Jeśli nie istnieje, zostaje utworzony.
     *
     * Produkcyjnie warto byłoby mieć osobny proces zarządzania użytkownikami,
     * ale na tym etapie to uproszczenie jest akceptowalne.
     */
    private final CustomerRepository customerRepository;

    /**
     * Repozytorium rezerwacji.
     *
     * Odpowiada za zapis i odczyt Reservation.
     * Dla odczytów DTO używamy metod typu findDetailedById, żeby uniknąć
     * przypadkowego problemu lazy loadingu podczas mapowania odpowiedzi.
     */
    private final ReservationRepository reservationRepository;

    /**
     * Repozytorium puli miejsc.
     *
     * To repozytorium jest krytyczne dla concurrency.
     * Metoda reserveOneSeatIfAvailable(...) powinna wykonywać atomowy UPDATE:
     *
     * UPDATE capacity_pools
     * SET available_capacity = available_capacity - 1
     * WHERE event_id = ?
     *   AND available_capacity > 0
     *
     * Dzięki temu check-then-act dzieje się w bazie danych jako jedna operacja,
     * a nie jako osobne:
     *
     * 1. SELECT available_capacity
     * 2. if available > 0
     * 3. UPDATE available_capacity
     *
     * Ten atomowy update chroni przed lost update i oversellingiem.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Cache detali eventu.
     *
     * Dodany w etapie NoSQL/cache. Cache jest tylko warstwą pomocniczą,
     * nie źródłem prawdy.
     */
    private final EventDetailsCache eventDetailsCache;

    /**
     * Cache snapshotu dostępności.
     *
     * Może przechowywać np. ostatnio znaną liczbę dostępnych miejsc.
     * Taki snapshot może być chwilowo nieaktualny, dlatego nie wolno na jego
     * podstawie podejmować krytycznej decyzji o sprzedaży miejsca.
     *
     * Decyzja o rezerwacji nadal musi przejść przez PostgreSQL.
     */
    private final AvailabilitySnapshotCache availabilitySnapshotCache;

    /**
     * Constructor injection.
     *
     * Przy tej liczbie zależności widać też pewien sygnał ostrzegawczy:
     * serwis zaczyna robić sporo rzeczy. Na tym etapie projektu jest to jeszcze
     * akceptowalne, bo to zwykły monolit edukacyjny, ale przy dalszym rozwoju
     * można by rozważyć wydzielenie np. ReservationCacheInvalidator.
     */
    public ReservationService(
            EventRepository eventRepository,
            CustomerRepository customerRepository,
            ReservationRepository reservationRepository,
            CapacityPoolRepository capacityPoolRepository,
            EventDetailsCache eventDetailsCache,
            AvailabilitySnapshotCache availabilitySnapshotCache
    ) {
        this.eventRepository = eventRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
        this.capacityPoolRepository = capacityPoolRepository;
        this.eventDetailsCache = eventDetailsCache;
        this.availabilitySnapshotCache = availabilitySnapshotCache;
    }

    /**
     * Tworzy rezerwację dla eventu.
     *
     * Ta metoda jest transakcyjna, ponieważ kilka operacji musi być spójnych:
     *
     * - event musi istnieć,
     * - dostępność musi zostać zmniejszona,
     * - klient musi zostać pobrany albo utworzony,
     * - rezerwacja musi zostać zapisana,
     * - cache musi zostać unieważniony po zmianie dostępności.
     *
     * Krytyczna część to zmniejszenie dostępności. Nie robimy tego przez
     * pobranie CapacityPool do pamięci i zwykłe pool.decrease(), bo to byłoby
     * podatne na race condition.
     */
    @Transactional
    public ReservationResponse create(UUID eventId, ReservationCreateRequest request) {
        /*
         * Najpierw sprawdzamy, czy event istnieje.
         *
         * To nie jest jeszcze mechanizm ochrony przed oversellingiem.
         * To tylko walidacja istnienia zasobu biznesowego.
         */
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        /*
         * Atomowa próba zarezerwowania jednego miejsca.
         *
         * Jeżeli update zwróci 1, miejsce zostało zarezerwowane.
         * Jeżeli update zwróci 0, oznacza to zwykle, że:
         *
         * - event nie ma już dostępnych miejsc,
         * - albo capacity pool nie istnieje.
         *
         * W tym projekcie traktujemy to jako brak dostępnej pojemności.
         */
        int updatedRows = capacityPoolRepository.reserveOneSeatIfAvailable(eventId);

        if (updatedRows != 1) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        /*
         * Pobieramy klienta po emailu albo tworzymy nowego.
         *
         * Uwaga: przy dużej konkurencji ten fragment może wymagać unikalnego
         * indeksu na emailu i obsługi konfliktu zapisu. Inaczej dwa równoległe
         * requesty dla tego samego nowego emaila mogą próbować utworzyć klienta
         * jednocześnie.
         */
        Customer customer = customerRepository.findByEmail(request.customerEmail())
                .orElseGet(() -> customerRepository.save(
                        new Customer(request.customerFullName(), request.customerEmail())
                ));

        /*
         * Zapisujemy rezerwację w statusie początkowym, zwykle PENDING.
         *
         * Sam fakt utworzenia rezerwacji oznacza, że miejsce zostało już zdjęte
         * z dostępnej puli.
         */
        Reservation reservation = reservationRepository.save(new Reservation(event, customer));

        /*
         * Po zmianie dostępności unieważniamy cache eventu.
         *
         * Ważna uwaga: cache invalidation dzieje się wewnątrz transakcji.
         * To jest proste, ale nieidealne. Jeżeli transakcja później się wycofa,
         * cache może zostać niepotrzebnie wyczyszczony.
         *
         * To zwykle jest bezpieczniejsze niż zostawienie starego cache,
         * ale produkcyjnie można rozważyć invalidację po commicie transakcji.
         */
        evictEventCaches(eventId);

        /*
         * Zwracamy świeży widok rezerwacji.
         *
         * get(...) wykona dodatkowe zapytanie, ale dzięki temu odpowiedź jest
         * mapowana przez jedną ścieżkę i zawiera relacje potrzebne do DTO.
         */
        return get(reservation.getId());
    }

    /**
     * Potwierdza rezerwację.
     *
     * Metoda działa w transakcji, ponieważ zmiana statusu rezerwacji powinna być
     * zapisana atomowo.
     *
     * W tym wariancie confirm nie zmienia dostępności — miejsce zostało zdjęte
     * z puli już przy utworzeniu rezerwacji.
     */
    @Transactional
    public ReservationResponse confirm(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        /*
         * Logika przejścia statusu powinna być zamknięta w encji Reservation.
         *
         * Dzięki temu serwis nie musi wiedzieć, z jakiego statusu można przejść
         * do CONFIRMED. Encja pilnuje własnych reguł.
         */
        reservation.confirm();

        /*
         * Nie ma jawnego reservationRepository.save(...), bo encja jest zarządzana
         * przez persistence context. Hibernate wykona dirty checking i zapisze
         * zmianę przy flush/commit.
         */
        return get(reservationId);
    }

    /**
     * Anuluje rezerwację.
     *
     * Jeśli anulowanie faktycznie zmieniło stan rezerwacji, zwalniamy jedno miejsce.
     * Jeśli rezerwacja była już anulowana albo jej anulowanie nie powinno zwalniać
     * miejsca, releaseOneSeat(...) nie powinien zostać wykonany.
     */
    @Transactional
    public ReservationResponse cancel(UUID reservationId) {
        /*
         * Używamy findDetailedById, bo za chwilę potrzebujemy eventu do zwolnienia
         * miejsca oraz do invalidacji cache.
         */
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        /*
         * Encja decyduje, czy anulowanie rzeczywiście nastąpiło.
         *
         * Zwrócenie boolean jest praktyczne, bo serwis wie wtedy, czy ma wykonać
         * efekt uboczny: zwolnienie miejsca.
         */
        boolean cancelledNow = reservation.cancel();

        if (cancelledNow) {
            UUID eventId = reservation.getEvent().getId();

            /*
             * Oddajemy jedno miejsce do puli.
             *
             * Warto upewnić się w repozytorium/bazie, że available_capacity nigdy
             * nie przekroczy total_capacity. Inaczej błąd w logice anulowania mógłby
             * sztucznie zwiększyć dostępność.
             */
            capacityPoolRepository.releaseOneSeat(eventId);

            /*
             * Zmieniliśmy dostępność, więc snapshoty cache nie są już wiarygodne.
             */
            evictEventCaches(eventId);
        }

        return get(reservationId);
    }

    /**
     * Pobiera rezerwację po ID i mapuje ją do DTO.
     *
     * readOnly = true oznacza, że metoda jest tylko odczytowa.
     *
     * findDetailedById powinno pobrać relacje potrzebne do odpowiedzi:
     * - event,
     * - customer.
     *
     * Dzięki temu unikamy LazyInitializationException podczas mapowania DTO.
     */
    @Transactional(readOnly = true)
    public ReservationResponse get(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        return toResponse(reservation);
    }

    /**
     * Unieważnia cache związany z eventem.
     *
     * Cache jest tylko optymalizacją odczytu. Po każdej zmianie, która wpływa
     * na dostępność albo detale eventu widoczne dla klienta, cache powinien zostać
     * usunięty.
     *
     * To jest strategia cache-aside:
     * - przy odczycie najpierw próbujemy cache,
     * - przy braku cache pobieramy z bazy,
     * - po zmianie danych usuwamy stary wpis z cache.
     */
    private void evictEventCaches(UUID eventId) {
        eventDetailsCache.evict(eventId);
        availabilitySnapshotCache.evict(eventId);
    }

    /**
     * Mapuje encję Reservation na DTO odpowiedzi.
     *
     * Nie zwracamy encji JPA bezpośrednio z API, ponieważ encje:
     *
     * - mogą zawierać pola techniczne,
     * - mogą mieć relacje lazy,
     * - mogą powodować cykliczną serializację,
     * - nie powinny być publicznym kontraktem HTTP.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt()
        );
    }
}