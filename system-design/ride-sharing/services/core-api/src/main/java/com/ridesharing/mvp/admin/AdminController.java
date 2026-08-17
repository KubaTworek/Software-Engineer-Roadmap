package com.ridesharing.mvp.admin;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import com.ridesharing.mvp.driver.Driver;
import com.ridesharing.mvp.driver.DriverRepository;
import com.ridesharing.mvp.ride.*;
import com.ridesharing.mvp.support.SupportService;
import com.ridesharing.mvp.support.SupportTicketDto;
import com.ridesharing.mvp.support.SupportTicketStatus;
import com.ridesharing.mvp.user.AppUser;
import com.ridesharing.mvp.user.AppUserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kontroler administracyjny dla operacji operatorskich.
 *
 * W kontekście aplikacji ride-sharing ten controller nie obsługuje zwykłego flow pasażera
 * ani kierowcy. Służy do nadzoru systemu: podglądu użytkowników, kierowców, przejazdów,
 * historii statusów oraz ticketów supportowych.
 *
 * Wszystkie endpointy są dostępne wyłącznie dla użytkowników z rolą ADMIN.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    /**
     * Repozytorium użytkowników aplikacji.
     * Używane tutaj do szybkiego podglądu liczby kont i listy ostatnich użytkowników.
     */
    private final AppUserRepository users;

    /**
     * Repozytorium kierowców.
     * Daje adminowi dostęp do podstawowego podglądu zarejestrowanych kierowców.
     */
    private final DriverRepository drivers;

    /**
     * Główna usługa przejazdów.
     * AdminController nie zmienia stanu przejazdu bezpośrednio w bazie,
     * tylko deleguje to do RideService, żeby zachować reguły domenowe i historię zmian.
     */
    private final RideService rideService;

    /**
     * Usługa supportu.
     * Obsługuje listowanie ticketów i logikę zgłoszeń użytkowników.
     */
    private final SupportService supportService;

    /**
     * Zwraca szybki snapshot stanu systemu dla panelu admina.
     *
     * To nie jest endpoint analityczny ani raportowy — zwraca lekkie, bieżące dane:
     * liczbę użytkowników, kierowców, ostatnio załadowane przejazdy i otwarte tickety.
     *
     * Uwaga: recentRidesLoaded i openTicketsLoaded są liczone na podstawie aktualnie
     * pobranej listy, a nie jako pełny COUNT z bazy. To dobre dla MVP/panelu operacyjnego,
     * ale przy dużej skali lepiej zastąpić to osobnymi zapytaniami count albo metrykami.
     */
    @GetMapping("/overview")
    public AdminOverview overview() {
        return new AdminOverview(
                users.count(),
                drivers.count(),
                rideService.listRecent(null).size(),
                supportService.list(SupportTicketStatus.OPEN).size(),
                Instant.now());
    }

    /**
     * Zwraca maksymalnie 100 użytkowników.
     *
     * Limit chroni panel admina przed przypadkowym pobraniem całej tabeli użytkowników.
     * W produkcji ten endpoint powinien mieć paginację, sortowanie i filtrowanie.
     */
    @GetMapping("/users")
    public List<AppUser> users() {
        return users.findAll().stream().limit(100).toList();
    }

    /**
     * Zwraca maksymalnie 100 kierowców.
     *
     * Podobnie jak przy użytkownikach, jest to prosty endpoint MVP.
     * Docelowo powinien obsługiwać paginację, status kierowcy, miasto, rating i weryfikację.
     */
    @GetMapping("/drivers")
    public List<Driver> drivers() {
        return drivers.findAll().stream().limit(100).toList();
    }

    /**
     * Zwraca ostatnie przejazdy.
     *
     * Opcjonalny parametr status pozwala adminowi zawęzić listę np. do przejazdów:
     * REQUESTED, MATCHING, DRIVER_ASSIGNED, IN_PROGRESS, COMPLETED albo CANCELLED.
     *
     * To podstawowe narzędzie operacyjne do monitorowania aktualnych i ostatnich kursów.
     */
    @GetMapping("/rides")
    public List<RideDto> rides(@RequestParam(required = false) RideStatus status) {
        return rideService.listRecent(status);
    }

    /**
     * Zwraca historię zmian statusów konkretnego przejazdu.
     *
     * Ten endpoint jest kluczowy przy debugowaniu problemów typu:
     * - pasażer twierdzi, że kierowca nie przyjechał,
     * - kierowca twierdzi, że pasażer anulował,
     * - płatność lub anulowanie nastąpiły w nietypowym momencie,
     * - trzeba sprawdzić dokładną kolejność przejść state machine.
     */
    @GetMapping("/rides/{rideId}/history")
    public List<RideStatusHistory> rideHistory(@PathVariable UUID rideId) {
        return rideService.history(rideId);
    }

    /**
     * Awaryjne anulowanie przejazdu przez administratora.
     *
     * To endpoint operatorski, który powinien być używany w sytuacjach wyjątkowych:
     * problem bezpieczeństwa, błąd aplikacji, zgłoszenie supportowe albo zablokowany przejazd.
     *
     * Przekazujemy principal.user(), żeby RideService wiedział, kto wykonał akcję.
     * Dzięki temu anulowanie może zostać zapisane w historii/audycie jako akcja admina,
     * a nie zwykłe anulowanie przez pasażera lub kierowcę.
     */
    @PostMapping("/rides/{rideId}/force-cancel")
    public RideDto forceCancel(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable UUID rideId,
                               @RequestBody ForceCancelRequest request) {
        return rideService.forceCancelByAdmin(rideId, principal.user(), request.reason());
    }

    /**
     * Zwraca tickety supportowe.
     *
     * Opcjonalny status pozwala filtrować zgłoszenia, np. OPEN, IN_PROGRESS, RESOLVED.
     * To podstawowy widok pracy supportu i administracji.
     */
    @GetMapping("/support/tickets")
    public List<SupportTicketDto> tickets(@RequestParam(required = false) SupportTicketStatus status) {
        return supportService.list(status);
    }

    /**
     * Request body dla awaryjnego anulowania przejazdu.
     *
     * reason jest wymagany, bo akcje admina powinny być audytowalne.
     * Bez powodu trudno później wyjaśnić, dlaczego przejazd został wymuszonym trybem anulowany.
     */
    public record ForceCancelRequest(@NotBlank String reason) {}

    /**
     * Lekki DTO dla dashboardu admina.
     *
     * generatedAt pozwala frontendowi pokazać, z którego momentu pochodzi snapshot.
     */
    public record AdminOverview(
            long users,
            long drivers,
            int recentRidesLoaded,
            int openTicketsLoaded,
            Instant generatedAt
    ) {}
}