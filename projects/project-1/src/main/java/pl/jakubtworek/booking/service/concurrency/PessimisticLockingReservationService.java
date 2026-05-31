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
 * Serwis pokazujący strategię pessimistic lockingu.
 *
 * To jest jedna ze strategii z Etapu 2 — Concurrency.
 *
 * Idea:
 *
 * - pobieramy CapacityPool z blokadą zapisu,
 * - baza blokuje konkretny wiersz puli miejsc,
 * - inne transakcje próbujące zablokować ten sam wiersz muszą poczekać,
 * - dopiero po zakończeniu pierwszej transakcji kolejna może kontynuować.
 *
 * W PostgreSQL odpowiada to zwykle zapytaniu typu:
 *
 * SELECT ...
 * FOR UPDATE
 *
 * Dzięki temu klasyczny kod:
 *
 * if (available > 0) {
 *     available--;
 * }
 *
 * jest chroniony przez blokadę bazodanową, a nie tylko przez mechanizm w Javie.
 *
 * W przeciwieństwie do synchronized ta strategia działa również wtedy,
 * gdy aplikacja ma kilka instancji, ponieważ blokada jest trzymana w bazie.
 */
@Service
public class PessimisticLockingReservationService {

    /**
     * Repozytorium puli miejsc.
     *
     * Używamy tu metody findByEventIdForUpdate(...), która pobiera CapacityPool
     * z blokadą PESSIMISTIC_WRITE.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Wspólny helper do tworzenia rezerwacji.
     *
     * Pozwala skupić tę klasę na strategii concurrency, a nie duplikować kodu:
     * - pobierania eventu,
     * - pobierania/tworzenia klienta,
     * - zapisywania rezerwacji.
     */
    private final ReservationCreationSupport reservationCreationSupport;

    /**
     * Constructor injection.
     */
    public PessimisticLockingReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Tworzy rezerwację z użyciem blokady pesymistycznej.
     *
     * Cała metoda działa w jednej transakcji.
     *
     * To jest krytyczne: blokada pobrana przez SELECT FOR UPDATE jest trzymana
     * tylko do końca transakcji. Gdyby nie było @Transactional, blokada mogłaby
     * zostać zwolniona zbyt wcześnie albo zachowanie zależałoby od autocommit.
     */
    @Transactional
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        /*
         * Pobieramy event.
         *
         * To nie jest jeszcze część blokowania dostępności, tylko walidacja,
         * że event istnieje.
         */
        Event event = reservationCreationSupport.getEvent(eventId);

        /*
         * Pobieramy CapacityPool z blokadą zapisu.
         *
         * Metoda repozytorium ma:
         *
         * @Lock(LockModeType.PESSIMISTIC_WRITE)
         *
         * Dla relacyjnej bazy oznacza to zwykle blokadę wiersza.
         *
         * Jeśli wiele requestów próbuje rezerwować ten sam event, tylko jeden
         * z nich naraz będzie mógł przejść przez ten fragment dla tej samej puli.
         */
        CapacityPool pool = capacityPoolRepository.findByEventIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        /*
         * Po pobraniu blokady sprawdzamy dostępność.
         *
         * Ten check-then-act jest tutaj bezpieczniejszy niż w wariancie naiwnym,
         * bo inne transakcje nie mogą równolegle zmodyfikować tego samego wiersza
         * bez poczekania na zwolnienie blokady.
         */
        if (pool.getAvailableCapacity() <= 0) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        /*
         * Zmniejszamy dostępność w modelu obiektowym.
         *
         * Hibernate wykryje zmianę przez dirty checking i wykona UPDATE przy
         * flush/commit transakcji.
         */
        pool.reserveOne();

        /*
         * Tworzymy rezerwację.
         *
         * Dopóki transakcja trwa, blokada puli miejsc jest nadal trzymana.
         * To oznacza, że im więcej pracy wykonasz w tej transakcji, tym dłużej
         * inne requesty będą czekały na ten sam event.
         */
        return reservationCreationSupport.saveReservation(event, request).getId();
    }
}