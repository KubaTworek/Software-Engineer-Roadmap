package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.driver.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler obsługujący akcje kierowcy na przypisanych przejazdach.
 *
 * W aplikacji ride-sharing ta klasa reprezentuje operacje wykonywane z aplikacji kierowcy:
 * - akceptacja oferty przejazdu,
 * - odrzucenie oferty,
 * - zgłoszenie przyjazdu na miejsce odbioru,
 * - rozpoczęcie kursu,
 * - zakończenie kursu.
 *
 * Controller nie powinien sam zmieniać statusów przejazdu.
 * Cała logika state machine, walidacja kierowcy i skutki uboczne powinny być w RideService.
 */
@RestController
@RequestMapping("/api/v1/driver/rides")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverRideController {

    /**
     * Główna usługa przejazdów.
     *
     * To RideService powinien być właścicielem cyklu życia przejazdu:
     * OFFERED -> ACCEPTED -> ARRIVED -> IN_PROGRESS -> COMPLETED.
     */
    private final RideService rideService;

    /**
     * Serwis kierowców.
     *
     * Używany do ustalenia, który Driver jest powiązany z aktualnie zalogowanym użytkownikiem.
     * Dzięki temu kierowca nie może wykonać akcji jako inny driver przez podstawienie cudzego ID.
     */
    private final DriverService driverService;

    /**
     * Akceptuje ofertę przejazdu.
     *
     * Flow:
     * 1. Pobiera profil kierowcy dla zalogowanego użytkownika.
     * 2. Przekazuje driver + rideId do RideService.
     * 3. RideService powinien sprawdzić, czy ten kierowca faktycznie dostał tę ofertę.
     * 4. Po sukcesie przejazd przechodzi do kolejnego statusu, np. DRIVER_ASSIGNED.
     *
     * To krytyczny endpoint dla matchingu, bo finalizuje przypisanie kierowcy do pasażera.
     */
    @PostMapping("/{rideId}/accept")
    public RideDto accept(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId
    ) {
        var driver = driverService.getByUser(principal.user());
        return rideService.acceptRide(driver, rideId);
    }

    /**
     * Odrzuca ofertę przejazdu.
     *
     * Po odrzuceniu RideService powinien:
     * - sprawdzić, czy oferta była przypisana do tego kierowcy,
     * - przywrócić kierowcę do AVAILABLE albo innego właściwego statusu,
     * - kontynuować matching z kolejnym kandydatem albo oznaczyć przejazd jako failed.
     */
    @PostMapping("/{rideId}/reject")
    public RideDto reject(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId
    ) {
        var driver = driverService.getByUser(principal.user());
        return rideService.rejectRide(driver, rideId);
    }

    /**
     * Oznacza, że kierowca dotarł do punktu odbioru.
     *
     * Ten status jest ważny dla pasażera i supportu:
     * - pasażer widzi, że auto czeka,
     * - można zacząć naliczać czas oczekiwania,
     * - łatwiej rozstrzygać reklamacje dotyczące anulowania lub no-show.
     */
    @PostMapping("/{rideId}/arrived")
    public RideDto arrived(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId
    ) {
        var driver = driverService.getByUser(principal.user());
        return rideService.markArrived(driver, rideId);
    }

    /**
     * Rozpoczyna przejazd.
     *
     * RideService powinien sprawdzić:
     * - czy przejazd jest przypisany do tego kierowcy,
     * - czy aktualny status pozwala na start,
     * - czy przejazd nie został anulowany,
     * - czy płatność/autoryzacja jest w akceptowalnym stanie.
     *
     * Po starcie kierowca powinien być oznaczony jako ON_TRIP,
     * a przejazd jako IN_PROGRESS.
     */
    @PostMapping("/{rideId}/start")
    public RideDto start(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId
    ) {
        var driver = driverService.getByUser(principal.user());
        return rideService.startRide(driver, rideId);
    }

    /**
     * Kończy przejazd.
     *
     * To jeden z najważniejszych endpointów w całym flow.
     *
     * Po zakończeniu RideService powinien:
     * - zmienić status przejazdu na COMPLETED,
     * - wyliczyć finalną cenę,
     * - uruchomić capture płatności,
     * - zapisać historię statusów,
     * - wysłać event outbox/Kafka,
     * - opublikować aktualizację przez WebSocket,
     * - przywrócić kierowcę do dostępności, jeżeli może przyjmować kolejne kursy.
     */
    @PostMapping("/{rideId}/complete")
    public RideDto complete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID rideId
    ) {
        var driver = driverService.getByUser(principal.user());
        return rideService.completeRide(driver, rideId);
    }
}