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
 * Serwis pokazujący strategię ochrony przed race condition przez synchronized.
 *
 * To jest rozwiązanie edukacyjne z Etapu 2 — Concurrency.
 *
 * Idea:
 *
 * - wiele requestów próbuje zarezerwować miejsce na ten sam event,
 * - metoda create(...) jest synchronized,
 * - więc w ramach jednej instancji aplikacji tylko jeden wątek naraz może wejść
 *   do tej metody.
 *
 * Dzięki temu lokalnie unikamy klasycznego check-then-act:
 *
 * if (available > 0) {
 *     available--;
 *     save reservation;
 * }
 *
 * Ale to rozwiązanie ma poważne ograniczenia:
 *
 * - działa tylko w jednej JVM,
 * - nie chroni przy kilku instancjach aplikacji,
 * - zmniejsza równoległość globalnie dla całej metody,
 * - blokuje także rezerwacje na różne eventy,
 * - nie rozwiązuje problemu na poziomie bazy danych.
 *
 * W projekcie docelowo bezpieczniejszy jest atomowy SQL update
 * albo odpowiednio dobrany locking na poziomie bazy.
 */
@Service
public class SynchronizedReservationService {

    /**
     * Repozytorium puli dostępności.
     *
     * W tym serwisie używane do zwykłego odczytu CapacityPool po eventId.
     * Sama metoda findByEventId(...) nie zakłada blokady bazodanowej.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Pomocniczy serwis do tworzenia rezerwacji.
     *
     * Wydzielenie ReservationCreationSupport pozwala uniknąć duplikowania kodu:
     * - pobrania eventu,
     * - znalezienia lub utworzenia klienta,
     * - zapisania rezerwacji.
     *
     * Dzięki temu różne strategie concurrency mogą skupiać się na różnicy
     * w aktualizacji CapacityPool.
     */
    private final ReservationCreationSupport reservationCreationSupport;

    /**
     * Constructor injection.
     */
    public SynchronizedReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Tworzy rezerwację z lokalną ochroną synchronized.
     *
     * @Transactional oznacza, że operacje JPA w tej metodzie wykonują się
     * w jednej transakcji.
     *
     * synchronized oznacza, że w obrębie jednej instancji tego beana tylko jeden
     * wątek naraz może wykonywać tę metodę.
     *
     * Ważne:
     * ponieważ Spring domyślnie tworzy beany jako singletony, synchronized blokuje
     * na tej jednej instancji serwisu.
     *
     * Gdy aplikacja działa w kilku instancjach, każda instancja ma własny obiekt
     * SynchronizedReservationService i własny monitor. Wtedy synchronized nie
     * chroni globalnie przed oversellingiem.
     */
    @Transactional
    public synchronized UUID create(UUID eventId, ReservationCreateRequest request) {
        /*
         * Pobieramy event przez wspólny helper.
         *
         * Jeżeli event nie istnieje, helper powinien rzucić NotFoundException.
         */
        Event event = reservationCreationSupport.getEvent(eventId);

        /*
         * Pobieramy pulę miejsc bez blokady pesymistycznej.
         *
         * W tej konkretnej strategii ochroną jest synchronized, a nie SELECT FOR UPDATE.
         */
        CapacityPool pool = capacityPoolRepository.findByEventId(eventId)
                .orElseThrow(() -> new NotFoundException("Capacity pool not found for event: " + eventId));

        /*
         * Klasyczny check-then-act.
         *
         * Normalnie to byłby kod podatny na race condition.
         * Tutaj jest względnie bezpieczny tylko dlatego, że metoda jest synchronized
         * i działa w jednej instancji JVM.
         */
        if (pool.getAvailableCapacity() <= 0) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        /*
         * Zmniejszamy dostępność w modelu obiektowym.
         *
         * Hibernate zapisze zmianę przez dirty checking przy flush/commit transakcji.
         */
        pool.reserveOne();

        /*
         * Tworzymy rezerwację.
         *
         * Zwracamy tylko UUID, bo testy concurrency zwykle interesuje liczba
         * utworzonych rezerwacji i końcowy stan puli, nie pełne DTO.
         */
        return reservationCreationSupport.saveReservation(event, request).getId();
    }
}