package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

/**
 * Celowo błędny serwis rezerwacji używany w Etapie 2 — Concurrency.
 *
 * Ta klasa pokazuje naiwną implementację:
 *
 * if (pool.getAvailableCapacity() > 0) {
 *     pool.setAvailableCapacity(pool.getAvailableCapacity() - 1);
 *     reservationRepository.save(...);
 * }
 *
 * Problem polega na tym, że sprawdzenie dostępności i zmniejszenie dostępności
 * nie są jedną atomową operacją.
 *
 * Przy wielu równoległych requestach kilka transakcji może:
 *
 * - odczytać tę samą wartość availableCapacity,
 * - uznać, że miejsce jest dostępne,
 * - utworzyć rezerwację,
 * - zapisać błędną albo nadpisaną wartość dostępności.
 *
 * Ta implementacja istnieje wyłącznie po to, żeby test współbieżny mógł pokazać
 * błąd, a potem porównać go z poprawnymi strategiami.
 */
@Service
public class NaiveReservationService {

    /**
     * Repozytorium puli dostępności.
     *
     * W tej klasie używane celowo w niebezpieczny sposób:
     * najpierw zwykły odczyt, potem osobny blind update.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Helper do wspólnej części tworzenia rezerwacji.
     *
     * Pozwala tej klasie skupić się na błędnej strategii concurrency,
     * a nie duplikować logiki pobierania eventu, klienta i zapisu rezerwacji.
     */
    private final ReservationCreationSupport reservationCreationSupport;

    /**
     * Constructor injection.
     */
    public NaiveReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Celowo błędna implementacja tworzenia rezerwacji.
     *
     * Metoda jest transakcyjna, ale sama transakcja nie wystarcza do ochrony
     * przed race condition.
     *
     * Dlaczego?
     *
     * Domyślny poziom izolacji bazy zwykle nie sprawia, że zwykły schemat:
     *
     * SELECT -> if -> UPDATE
     *
     * staje się bezpieczny dla wielu równoległych transakcji.
     *
     * Tutaj dodatkowo używamy blindSetAvailableCapacity(...), które celowo omija
     * mechanizm @Version i pozwala łatwiej pokazać lost update.
     */
    @Transactional
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        /*
         * Pobieramy event.
         *
         * To nie jest problematyczna część concurrency.
         * Problem zaczyna się przy odczycie i aktualizacji CapacityPool.
         */
        Event event = reservationCreationSupport.getEvent(eventId);

        /*
         * Pobieramy CapacityPool zwykłym SELECT-em.
         *
         * Nie ma tutaj:
         * - SELECT FOR UPDATE,
         * - optimistic locking retry,
         * - atomowego UPDATE ... WHERE available_capacity > 0,
         * - synchronizacji między instancjami aplikacji.
         */
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        /*
         * Pierwsza część błędnego wzorca: check.
         *
         * Ten warunek może być prawdziwy jednocześnie dla wielu transakcji,
         * bo każda z nich może widzieć starą wartość availableCapacity.
         */
        if (pool.getAvailableCapacity() <= 0) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        /*
         * Celowo poszerzamy okno wyścigu.
         *
         * Bez tego race condition nadal może istnieć, ale test może być niestabilny:
         * czasem się ujawni, czasem nie.
         *
         * Sleep zwiększa szansę, że wiele wątków odczyta tę samą wartość
         * availableCapacity zanim którykolwiek wykona update.
         */
        widenRaceWindow();

        /*
         * Druga część błędnego wzorca: read-modify-write.
         *
         * Liczymy nową wartość na podstawie wcześniej odczytanego stanu encji.
         *
         * Przykład lost update:
         *
         * - availableCapacity = 10,
         * - transakcja A odczytuje 10,
         * - transakcja B odczytuje 10,
         * - A liczy 9,
         * - B liczy 9,
         * - A zapisuje 9,
         * - B zapisuje 9.
         *
         * Dwie rezerwacje powstały, ale dostępność spadła tylko o 1.
         */
        int newAvailableCapacity = pool.getAvailableCapacity() - 1;

        /*
         * Blind update celowo omija @Version.
         *
         * Gdybyśmy normalnie zmieniali encję CapacityPool z @Version,
         * Hibernate mógłby wykryć konflikt optimistic lockingu.
         *
         * Tutaj chcemy pokazać klasyczny błąd, więc zapisujemy wartość bez warunku:
         *
         * UPDATE capacity_pools
         * SET available_capacity = :availableCapacity
         * WHERE id = :poolId
         *
         * To nie sprawdza, czy ktoś już zmienił ten wiersz między odczytem a zapisem.
         */
        capacityPoolRepository.blindSetAvailableCapacity(pool.getId(), newAvailableCapacity);

        /*
         * Tworzymy rezerwację mimo tego, że stan puli mógł już być zmieniony
         * przez inne transakcje.
         *
         * To może prowadzić do sytuacji, w której liczba rezerwacji jest większa
         * niż faktyczna pojemność albo dostępność nie odpowiada liczbie rezerwacji.
         */
        return reservationCreationSupport.saveReservation(event, request).getId();
    }

    /**
     * Sztucznie powiększa okno czasowe wyścigu.
     *
     * To jest antywzorzec produkcyjny, ale bardzo przydatny w teście edukacyjnym.
     *
     * Dzięki Thread.sleep(...) test równoległy ma większą szansę deterministycznie
     * pokazać problem lost update.
     */
    private void widenRaceWindow() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            /*
             * Poprawna obsługa InterruptedException:
             * przywracamy flagę przerwania i kończymy metodę wyjątkiem.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reproducing race condition", e);
        }
    }
}