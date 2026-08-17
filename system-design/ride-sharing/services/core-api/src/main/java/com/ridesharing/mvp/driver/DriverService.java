package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.location.LocationService;
import com.ridesharing.mvp.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Serwis domenowy odpowiedzialny za profil, status i dostępność kierowcy.
 *
 * W aplikacji ride-sharing DriverService jest jednym z kluczowych elementów matchingu:
 * to tutaj system sprawdza, czy kierowca może być brany pod uwagę do kursu,
 * blokuje go na czas oferty i zmienia jego status w trakcie cyklu życia przejazdu.
 *
 * Controller tylko przyjmuje requesty. Reguły dotyczące kierowcy powinny być tutaj.
 */
@Service
@RequiredArgsConstructor
public class DriverService {

    /**
     * Repozytorium kierowców.
     *
     * Służy do pobierania profilu kierowcy, zapisu danych pojazdu
     * oraz atomowej zmiany statusu kierowcy podczas matchingu.
     */
    private final DriverRepository drivers;

    /**
     * Serwis lokalizacji.
     *
     * @Lazy jest użyte najpewniej po to, żeby przerwać cykliczną zależność
     * między DriverService i LocationService.
     *
     * To rozwiązanie działa, ale warto uważać: jeżeli zależności zaczynają się zapętlać,
     * często oznacza to, że odpowiedzialności serwisów są zbyt mocno splecione.
     */
    @Lazy
    private final LocationService locationService;

    /**
     * Pobiera profil kierowcy przypisany do danego użytkownika.
     *
     * Używane w endpointach typu /drivers/me, gdzie nie przekazujemy driverId z requestu,
     * tylko ustalamy kierowcę na podstawie aktualnie zalogowanego użytkownika.
     *
     * Dzięki temu użytkownik nie może operować na cudzym profilu kierowcy.
     */
    public Driver getByUser(AppUser user) {
        return drivers.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver profile not found"));
    }

    /**
     * Pobiera kierowcę po ID.
     *
     * Używane przez procesy systemowe, np. RideService albo MatchingService,
     * które operują już na konkretnym driverId zapisanym w przejeździe.
     */
    public Driver getById(UUID id) {
        return drivers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    /**
     * Tworzy albo aktualizuje profil kierowcy.
     *
     * Flow:
     * 1. Szuka profilu kierowcy dla danego użytkownika.
     * 2. Jeżeli profil nie istnieje, tworzy nowy.
     * 3. Ustawia dane pojazdu.
     * 4. Zapisuje profil i zwraca DTO.
     *
     * W MVP kierowca jest od razu oznaczany jako VERIFIED.
     * To skrót implementacyjny — w produkcji status powinien być raczej PENDING_VERIFICATION,
     * dopóki dokumenty, prawo jazdy, ubezpieczenie i pojazd nie zostaną sprawdzone.
     */
    @Transactional
    public DriverDto createOrUpdateProfile(AppUser user, DriverController.DriverProfileRequest request) {
        var driver = drivers.findByUser(user).orElseGet(() -> Driver.builder()
                .id(UUID.randomUUID())
                .user(user)

                /*
                 * MVP shortcut.
                 * Docelowo nie wolno automatycznie weryfikować kierowcy po samym uzupełnieniu profilu.
                 */
                .verificationStatus(DriverVerificationStatus.VERIFIED)

                /*
                 * Nowy kierowca startuje jako OFFLINE.
                 * Nie powinien trafiać do matchingu, dopóki sam nie przełączy się na AVAILABLE.
                 */
                .availabilityStatus(DriverAvailabilityStatus.OFFLINE)

                /*
                 * Startowy rating kierowcy.
                 * Może później wpływać na ranking w matchingu i decyzje fraud/support.
                 */
                .rating(BigDecimal.valueOf(5.00))

                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        /*
         * Dane pojazdu są ważne dla pasażera po przypisaniu kierowcy:
         * marka, model, kolor i tablica pomagają zidentyfikować auto.
         */
        driver.setVehicleMake(request.vehicleMake());
        driver.setVehicleModel(request.vehicleModel());
        driver.setPlateNumber(request.plateNumber());
        driver.setVehicleColor(request.vehicleColor());
        driver.setVehicleType(request.vehicleType());

        return DriverDto.from(drivers.save(driver));
    }

    /**
     * Aktualizuje dostępność kierowcy.
     *
     * Ten status bezpośrednio wpływa na matching:
     * tylko kierowcy AVAILABLE powinni być kandydatami do nowych przejazdów.
     *
     * Jeżeli kierowca nie jest zweryfikowany, nie może wejść do puli dostępnych kierowców.
     * To chroni system przed przypisywaniem przejazdów osobom bez zatwierdzonego profilu.
     */
    @Transactional
    public DriverDto updateAvailability(AppUser user, DriverAvailabilityStatus status) {
        var driver = getByUser(user);

        if (driver.getVerificationStatus() != DriverVerificationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Driver is not verified");
        }

        driver.setAvailabilityStatus(status);
        drivers.save(driver);

        /*
         * Jeżeli kierowca nie jest AVAILABLE, usuwamy go z indeksu lokalizacji.
         *
         * To jest ważne, bo Redis GEO / LocationService służy MatchingService do szukania kandydatów.
         * Kierowca OFFLINE, ON_TRIP albo OFFERED_RIDE nie powinien pojawiać się jako dostępny w pobliżu.
         */
        if (status != DriverAvailabilityStatus.AVAILABLE) {
            locationService.removeDriver(driver.getId());
        }

        return DriverDto.from(driver);
    }

    /**
     * Próbuje tymczasowo zarezerwować kierowcę dla oferty przejazdu.
     *
     * To jest krytyczna metoda dla matchingu.
     *
     * findWithLockById powinno pobrać rekord z blokadą pesymistyczną,
     * żeby dwa równoległe procesy matchingu nie przypisały tego samego kierowcy
     * do dwóch różnych pasażerów.
     *
     * Flow:
     * 1. Pobiera kierowcę z blokadą.
     * 2. Sprawdza, czy nadal jest AVAILABLE.
     * 3. Jeżeli tak, zmienia status na OFFERED_RIDE.
     * 4. Zwraca true.
     * 5. Jeżeli kierowca nie jest już dostępny, zwraca false.
     */
    @Transactional
    public boolean tryOfferDriver(UUID driverId) {
        var driver = drivers.findWithLockById(driverId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver not found"));

        if (driver.getAvailabilityStatus() != DriverAvailabilityStatus.AVAILABLE) {
            return false;
        }

        driver.setAvailabilityStatus(DriverAvailabilityStatus.OFFERED_RIDE);
        drivers.save(driver);

        return true;
    }

    /**
     * Oznacza kierowcę jako ponownie dostępnego.
     *
     * Używane np. gdy:
     * - kierowca odrzucił ofertę,
     * - oferta wygasła,
     * - przejazd został anulowany przed startem,
     * - system chce przywrócić kierowcę do puli matchingu.
     *
     * Trzeba uważać, żeby nie wywołać tej metody dla kierowcy, który jest już ON_TRIP.
     * W większym systemie warto walidować dozwolone przejścia statusów.
     */
    @Transactional
    public void markAvailable(UUID driverId) {
        var driver = getById(driverId);
        driver.setAvailabilityStatus(DriverAvailabilityStatus.AVAILABLE);
        drivers.save(driver);
    }

    /**
     * Oznacza kierowcę jako będącego w trakcie przejazdu.
     *
     * Ten status powinien być ustawiany po zaakceptowaniu/startowaniu kursu,
     * żeby kierowca zniknął z puli kandydatów dla kolejnych pasażerów.
     */
    @Transactional
    public void markOnTrip(UUID driverId) {
        var driver = getById(driverId);
        driver.setAvailabilityStatus(DriverAvailabilityStatus.ON_TRIP);
        drivers.save(driver);
    }
}