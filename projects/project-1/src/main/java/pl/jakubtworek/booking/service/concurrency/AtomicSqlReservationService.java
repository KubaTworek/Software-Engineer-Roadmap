package pl.jakubtworek.booking.service.concurrency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.entity.Event;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.repository.CapacityPoolRepository;

import java.util.UUID;

/**
 * Serwis pokazujący strategię atomowego update'u SQL.
 *
 * To jest jedna ze strategii z Etapu 2 — Concurrency.
 *
 * W tym projekcie jest to preferowane podejście do zmniejszania dostępności miejsc,
 * bo warunek i modyfikacja są wykonywane razem po stronie bazy danych.
 *
 * Zamiast robić w Javie:
 *
 * 1. SELECT available_capacity
 * 2. if available_capacity > 0
 * 3. UPDATE available_capacity = available_capacity - 1
 *
 * robimy jedno zapytanie:
 *
 * UPDATE capacity_pools
 * SET available_capacity = available_capacity - 1
 * WHERE event_id = :eventId
 *   AND available_capacity > 0
 *
 * Dzięki temu nie ma okna czasowego między sprawdzeniem dostępności
 * a zmniejszeniem dostępności.
 */
@Service
public class AtomicSqlReservationService {

    /**
     * Repozytorium puli miejsc.
     *
     * Najważniejsza metoda używana tutaj to reserveOneSeatIfAvailable(...),
     * która wykonuje atomowy native SQL update.
     */
    private final CapacityPoolRepository capacityPoolRepository;

    /**
     * Wspólny helper do tworzenia rezerwacji.
     *
     * Dzięki temu ta klasa skupia się wyłącznie na strategii concurrency,
     * a nie duplikuje kodu:
     * - pobierania eventu,
     * - pobierania/tworzenia klienta,
     * - zapisywania rezerwacji.
     */
    private final ReservationCreationSupport reservationCreationSupport;

    /**
     * Constructor injection.
     */
    public AtomicSqlReservationService(
            CapacityPoolRepository capacityPoolRepository,
            ReservationCreationSupport reservationCreationSupport
    ) {
        this.capacityPoolRepository = capacityPoolRepository;
        this.reservationCreationSupport = reservationCreationSupport;
    }

    /**
     * Tworzy rezerwację z użyciem atomowego update'u SQL.
     *
     * Metoda jest transakcyjna, bo zmniejszenie dostępności i zapis rezerwacji
     * powinny być częścią jednej jednostki pracy.
     *
     * Jeśli zapis rezerwacji nie powiedzie się po zmniejszeniu dostępności,
     * transakcja powinna zostać wycofana, a zmniejszenie dostępności również.
     */
    @Transactional
    public UUID create(UUID eventId, ReservationCreateRequest request) {
        /*
         * Pobieramy event.
         *
         * To waliduje istnienie eventu i daje encję potrzebną do zapisania Reservation.
         *
         * Sama ochrona przed oversellingiem nie wynika z tego SELECT-a.
         * Wynika z atomowego update'u poniżej.
         */
        Event event = reservationCreationSupport.getEvent(eventId);

        /*
         * Atomowa próba zarezerwowania jednego miejsca.
         *
         * Repozytorium wykonuje SQL w stylu:
         *
         * UPDATE capacity_pools
         * SET available_capacity = available_capacity - 1
         * WHERE event_id = ?
         *   AND available_capacity > 0
         *
         * Jeśli update zwróci 1, oznacza to, że miejsce zostało zdjęte z puli.
         * Jeśli update zwróci 0, oznacza to, że nie było dostępnych miejsc
         * albo nie znaleziono puli dla eventu.
         */
        int updatedRows = capacityPoolRepository.reserveOneSeatIfAvailable(eventId);

        /*
         * W poprawnym przypadku dokładnie jeden wiersz powinien zostać zmieniony.
         *
         * updatedRows == 0:
         * - brak miejsc,
         * - albo brak CapacityPool dla eventu.
         *
         * updatedRows > 1 nie powinno się zdarzyć, jeśli event_id jest unikalny
         * w capacity_pools. Gdyby się zdarzyło, oznaczałoby problem ze schematem danych.
         */
        if (updatedRows != 1) {
            throw new CapacityUnavailableException("No available capacity for event: " + eventId);
        }

        /*
         * Dopiero po skutecznym zdjęciu miejsca z puli zapisujemy rezerwację.
         *
         * Jeśli zapis rezerwacji rzuci wyjątek, @Transactional powinno wycofać
         * także wcześniejszy update dostępności.
         */
        return reservationCreationSupport.saveReservation(event, request).getId();
    }
}