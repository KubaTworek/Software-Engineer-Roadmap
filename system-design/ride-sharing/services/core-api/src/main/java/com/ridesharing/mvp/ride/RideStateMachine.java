package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * State machine kontrolująca dozwolone przejścia statusów przejazdu.
 *
 * W aplikacji ride-sharing to bardzo ważny komponent, bo przejazd ma wiele etapów
 * i nie każdy status może przejść w dowolny inny status.
 *
 * Przykład:
 * - przejazd IN_PROGRESS może przejść do COMPLETED,
 * - ale COMPLETED nie powinien wrócić do MATCHING,
 * - CANCELLED_BY_PASSENGER jest stanem końcowym.
 *
 * Ta klasa chroni RideService przed niepoprawnymi zmianami statusu,
 * szczególnie przy race condition, retry requestów i akcjach wykonywanych równolegle.
 */
@Component
public class RideStateMachine {

    /**
     * Mapa dozwolonych przejść.
     *
     * Klucz: aktualny status przejazdu.
     * Wartość: zbiór statusów, do których można legalnie przejść.
     *
     * EnumMap jest dobrym wyborem dla enumów:
     * jest szybki, lekki i czytelny.
     */
    private final EnumMap<RideStatus, EnumSet<RideStatus>> transitions =
            new EnumMap<>(RideStatus.class);

    /**
     * Definiuje graf przejść statusów przejazdu.
     *
     * To tutaj opisany jest lifecycle ride:
     * REQUESTED -> MATCHING -> DRIVER_ASSIGNED -> DRIVER_ARRIVING
     * -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
     *
     * Oprócz ścieżki głównej są też przejścia awaryjne:
     * - anulowanie przez pasażera,
     * - anulowanie przez kierowcę,
     * - wygaśnięcie matchingu,
     * - błąd techniczny.
     */
    public RideStateMachine() {
        /*
         * Przejazd został utworzony, ale matching jeszcze nie musi być rozpoczęty.
         *
         * Możliwe przejścia:
         * - MATCHING: system zaczyna szukać kierowcy,
         * - CANCELLED_BY_PASSENGER: pasażer anuluje od razu,
         * - FAILED: błąd techniczny lub biznesowy.
         */
        transitions.put(
                RideStatus.REQUESTED,
                EnumSet.of(
                        RideStatus.MATCHING,
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.FAILED
                )
        );

        /*
         * System szuka kierowcy.
         *
         * Możliwe przejścia:
         * - DRIVER_ASSIGNED: znaleziono/przypisano kierowcę,
         * - CANCELLED_BY_PASSENGER: pasażer rezygnuje podczas matchingu,
         * - EXPIRED: nie udało się znaleźć kierowcy w czasie,
         * - FAILED: błąd techniczny lub biznesowy.
         */
        transitions.put(
                RideStatus.MATCHING,
                EnumSet.of(
                        RideStatus.DRIVER_ASSIGNED,
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.EXPIRED,
                        RideStatus.FAILED
                )
        );

        /*
         * Kierowca został przypisany do przejazdu.
         *
         * Możliwe przejścia:
         * - DRIVER_ARRIVING: kierowca jedzie po pasażera,
         * - MATCHING: kierowca odrzucił/anulował i system wraca do szukania,
         * - CANCELLED_BY_PASSENGER: pasażer anuluje po przypisaniu kierowcy,
         * - CANCELLED_BY_DRIVER: kierowca anuluje,
         * - FAILED: błąd techniczny lub biznesowy.
         */
        transitions.put(
                RideStatus.DRIVER_ASSIGNED,
                EnumSet.of(
                        RideStatus.DRIVER_ARRIVING,
                        RideStatus.MATCHING,
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.CANCELLED_BY_DRIVER,
                        RideStatus.FAILED
                )
        );

        /*
         * Kierowca jest w drodze do punktu odbioru.
         *
         * Możliwe przejścia:
         * - DRIVER_ARRIVED: kierowca dotarł na miejsce,
         * - IN_PROGRESS: uproszczony flow, gdy start następuje bez osobnego ARRIVED,
         * - CANCELLED_BY_PASSENGER: pasażer anuluje,
         * - CANCELLED_BY_DRIVER: kierowca anuluje,
         * - FAILED: błąd techniczny lub biznesowy.
         */
        transitions.put(
                RideStatus.DRIVER_ARRIVING,
                EnumSet.of(
                        RideStatus.DRIVER_ARRIVED,
                        RideStatus.IN_PROGRESS,
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.CANCELLED_BY_DRIVER,
                        RideStatus.FAILED
                )
        );

        /*
         * Kierowca dotarł do pasażera.
         *
         * Możliwe przejścia:
         * - IN_PROGRESS: pasażer wsiadł i kurs się rozpoczął,
         * - CANCELLED_BY_PASSENGER: pasażer anuluje/no-show,
         * - CANCELLED_BY_DRIVER: kierowca anuluje,
         * - FAILED: błąd techniczny lub biznesowy.
         */
        transitions.put(
                RideStatus.DRIVER_ARRIVED,
                EnumSet.of(
                        RideStatus.IN_PROGRESS,
                        RideStatus.CANCELLED_BY_PASSENGER,
                        RideStatus.CANCELLED_BY_DRIVER,
                        RideStatus.FAILED
                )
        );

        /*
         * Przejazd trwa.
         *
         * Możliwe przejścia:
         * - COMPLETED: przejazd zakończony poprawnie,
         * - FAILED: awaryjne zakończenie przez błąd systemowy.
         *
         * Celowo nie ma tu zwykłego CANCELLED, bo po starcie kursu najczęściej
         * należy go zakończyć lub obsłużyć jako przypadek awaryjny, a nie anulować.
         */
        transitions.put(
                RideStatus.IN_PROGRESS,
                EnumSet.of(
                        RideStatus.COMPLETED,
                        RideStatus.FAILED
                )
        );

        /*
         * Stany końcowe.
         *
         * Po osiągnięciu jednego z tych statusów przejazd nie powinien już zmieniać stanu.
         */
        transitions.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        transitions.put(RideStatus.CANCELLED_BY_PASSENGER, EnumSet.noneOf(RideStatus.class));
        transitions.put(RideStatus.CANCELLED_BY_DRIVER, EnumSet.noneOf(RideStatus.class));
        transitions.put(RideStatus.EXPIRED, EnumSet.noneOf(RideStatus.class));
        transitions.put(RideStatus.FAILED, EnumSet.noneOf(RideStatus.class));
    }

    /**
     * Sprawdza, czy przejście ze statusu from do statusu to jest dozwolone.
     *
     * Jeżeli przejście jest poprawne, metoda nic nie robi.
     * Jeżeli przejście jest niepoprawne, rzuca ApiException z HTTP 409 CONFLICT.
     *
     * 409 jest tutaj właściwe, bo problemem nie jest format requestu,
     * tylko konflikt z aktualnym stanem przejazdu.
     */
    public void assertTransition(RideStatus from, RideStatus to) {
        /*
         * Powtórne ustawienie tego samego statusu traktujemy jako operację idempotentną.
         *
         * To pomaga przy retry requestów, np. gdy klient ponowi "start ride",
         * a przejazd jest już w IN_PROGRESS.
         */
        if (from == to) {
            return;
        }

        /*
         * Jeżeli status źródłowy nie ma zdefiniowanych przejść,
         * traktujemy go jak stan bez wyjść.
         */
        if (!transitions
                .getOrDefault(from, EnumSet.noneOf(RideStatus.class))
                .contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Invalid ride transition " + from + " -> " + to
            );
        }
    }
}