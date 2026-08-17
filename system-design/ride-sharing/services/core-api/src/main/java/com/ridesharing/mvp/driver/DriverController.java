package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.location.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler endpointów kierowcy zalogowanego jako DRIVER.
 *
 * W aplikacji ride-sharing ta klasa obsługuje operacje wykonywane z aplikacji kierowcy:
 * - utworzenie lub aktualizację profilu kierowcy,
 * - zmianę dostępności,
 * - wysyłanie aktualnej lokalizacji.
 *
 * Controller nie powinien sam decydować, czy kierowca może przyjąć kurs albo zmienić status
 * w trakcie aktywnego przejazdu. Takie reguły powinny być w DriverService / RideService.
 */
@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class DriverController {

    /**
     * Serwis odpowiedzialny za dane i status kierowcy.
     *
     * To tutaj powinny znajdować się reguły typu:
     * - czy użytkownik ma już profil kierowcy,
     * - czy można zmienić status dostępności,
     * - czy kierowca jest zweryfikowany,
     * - czy kierowca nie ma aktywnego przejazdu.
     */
    private final DriverService driverService;

    /**
     * Serwis lokalizacji kierowców.
     *
     * W MVP aktualizacja lokalizacji trafia przez API HTTP.
     * LocationService powinien zapisywać bieżącą pozycję w Redis GEO / cache live-state,
     * a nie jako każdy punkt GPS w relacyjnej bazie.
     */
    private final LocationService locationService;

    /**
     * Tworzy albo aktualizuje profil kierowcy.
     *
     * Endpoint służy do zapisania danych pojazdu, które są później widoczne dla pasażera
     * po przypisaniu kierowcy do przejazdu.
     *
     * principal.user() wskazuje aktualnie zalogowanego użytkownika.
     * Dzięki temu kierowca nie może aktualizować profilu innego kierowcy przez podanie cudzego ID.
     */
    @PostMapping("/profile")
    public DriverDto createOrUpdateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody DriverProfileRequest request) {
        return driverService.createOrUpdateProfile(principal.user(), request);
    }

    /**
     * Aktualizuje dostępność kierowcy, np. AVAILABLE albo OFFLINE.
     *
     * Ten status jest kluczowy dla matchingu:
     * tylko kierowcy dostępni powinni trafiać do puli kandydatów dla nowych przejazdów.
     *
     * Sama zmiana statusu nie powinna omijać reguł domenowych.
     * Przykład: kierowca w trakcie kursu nie powinien móc ręcznie ustawić AVAILABLE.
     */
    @PostMapping("/availability")
    public DriverDto updateAvailability(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody AvailabilityRequest request) {
        return driverService.updateAvailability(principal.user(), request.status());
    }

    /**
     * Aktualizuje bieżącą lokalizację kierowcy.
     *
     * Ten endpoint zasila real-time matching i mapę pasażera.
     * Matching Service będzie później szukał najbliższych dostępnych kierowców
     * na podstawie lokalizacji zapisanej przez LocationService.
     *
     * Flow:
     * 1. Pobiera profil kierowcy przypisany do zalogowanego użytkownika.
     * 2. Przekazuje współrzędne do LocationService.
     * 3. LocationService aktualizuje pozycję kierowcy w szybkim storage, np. Redis GEO.
     *
     * Endpoint zwraca void, bo klient nie potrzebuje pełnej odpowiedzi po każdej lokalizacji.
     * Przy częstych update’ach GPS odpowiedź powinna być możliwie lekka.
     */
    @PostMapping("/location")
    public void updateLocation(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody LocationRequest request) {
        var driver = driverService.getByUser(principal.user());

        locationService.updateDriverLocation(
                driver,
                request.lat(),
                request.lng(),
                request.heading(),
                request.speed()
        );
    }

    /**
     * Dane profilu pojazdu kierowcy.
     *
     * Są potrzebne pasażerowi po matchingu, żeby wiedział:
     * - jaki samochód przyjedzie,
     * - jaki jest numer rejestracyjny,
     * - jaki typ pojazdu został przypisany.
     */
    public record DriverProfileRequest(
            @NotBlank String vehicleMake,
            @NotBlank String vehicleModel,
            @NotBlank String plateNumber,
            @NotBlank String vehicleColor,
            @NotBlank String vehicleType
    ) {}

    /**
     * Request zmiany dostępności kierowcy.
     *
     * status nie może być null, ponieważ system musi jednoznacznie wiedzieć,
     * czy kierowca jest dostępny dla matchingu, niedostępny, czy zajęty.
     */
    public record AvailabilityRequest(
            @NotNull DriverAvailabilityStatus status
    ) {}

    /**
     * Aktualna pozycja GPS kierowcy.
     *
     * lat i lng mają walidację zakresu geograficznego:
     * - latitude: od -90 do 90,
     * - longitude: od -180 do 180.
     *
     * heading oznacza kierunek jazdy, a speed prędkość.
     * Te pola mogą później poprawić ETA, matching i wykrywanie podejrzanych skoków GPS.
     */
    public record LocationRequest(
            @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            double heading,
            double speed
    ) {}
}