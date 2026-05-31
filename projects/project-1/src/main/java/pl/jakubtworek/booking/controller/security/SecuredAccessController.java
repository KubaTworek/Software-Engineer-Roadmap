package pl.jakubtworek.booking.controller.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.dto.security.PaymentSummaryResponse;
import pl.jakubtworek.booking.dto.security.SecuredMessageResponse;
import pl.jakubtworek.booking.dto.security.UserSummaryResponse;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.ReservationService;

import java.util.List;
import java.util.UUID;

/**
 * Kontroler REST z endpointami chronionymi przez Spring Security.
 *
 * Ta klasa należy do etapu Security i autoryzacja.
 *
 * Celem nie jest tylko pokazanie:
 *
 * hasRole('ADMIN')
 *
 * ale przede wszystkim autoryzacji opartej o dane:
 *
 * - customer widzi tylko swoje rezerwacje,
 * - event manager widzi eventy swojej organizacji,
 * - org admin zarządza użytkownikami tylko w swoim tenantcie,
 * - HR widzi pracowników tylko swojej organizacji,
 * - support widzi ograniczony widok danych, bez pełnych danych płatności.
 *
 * @PreAuthorize działa przed wejściem do metody kontrolera.
 * Wyrażenie SpEL może odwoływać się do:
 *
 * - authentication,
 * - parametrów metody, np. #reservationId,
 * - beanów Springa, np. @reservationSecurity.
 */
@RestController
@RequestMapping("/api/secure")
public class SecuredAccessController {

    /**
     * Serwis rezerwacji.
     *
     * Kontroler używa go dopiero po przejściu autoryzacji z @PreAuthorize.
     */
    private final ReservationService reservationService;

    /**
     * Serwis eventów.
     *
     * Używany w manager view dla eventu.
     */
    private final EventService eventService;

    /**
     * Repozytorium użytkowników aplikacji.
     *
     * W tym kontrolerze jest użyte bezpośrednio do prostych list użytkowników.
     * Produkcyjnie można by rozważyć osobny AppUserService, żeby kontroler
     * nie znał repozytorium.
     */
    private final AppUserRepository appUserRepository;

    /**
     * Constructor injection.
     *
     * Zależności są jawne, pola są final, a klasa jest łatwiejsza do testowania.
     */
    public SecuredAccessController(ReservationService reservationService,
                                   EventService eventService,
                                   AppUserRepository appUserRepository) {
        this.reservationService = reservationService;
        this.eventService = eventService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Pobiera rezerwację po ID, ale tylko jeśli aktualny użytkownik ma prawo ją widzieć.
     *
     * Endpoint:
     *
     * GET /api/secure/reservations/{reservationId}
     *
     * Autoryzacja:
     *
     * @reservationSecurity.canView(authentication, #reservationId)
     *
     * To jest przykład autoryzacji opartej o dane.
     *
     * Sama rola nie wystarczy. CUSTOMER może mieć rolę CUSTOMER, ale nadal powinien
     * widzieć tylko własne rezerwacje, a nie wszystkie rezerwacje w systemie.
     */
    @GetMapping("/reservations/{reservationId}")
    @PreAuthorize("@reservationSecurity.canView(authentication, #reservationId)")
    public ReservationResponse getReservation(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }

    /**
     * Pobiera widok eventu dostępny dla managera.
     *
     * Endpoint:
     *
     * GET /api/secure/events/{eventId}/manager-view
     *
     * Autoryzacja:
     *
     * @eventSecurity.canManage(authentication, #eventId)
     *
     * To powinno sprawdzać nie tylko rolę EVENT_MANAGER, ale też to, czy event
     * należy do organizacji użytkownika.
     *
     * Innymi słowy:
     * manager organizacji A nie powinien zarządzać eventem organizacji B.
     */
    @GetMapping("/events/{eventId}/manager-view")
    @PreAuthorize("@eventSecurity.canManage(authentication, #eventId)")
    public EventResponse getManagerEventView(@PathVariable UUID eventId) {
        return eventService.get(eventId);
    }

    /**
     * Pobiera użytkowników organizacji.
     *
     * Endpoint:
     *
     * GET /api/secure/organizations/{organizationId}/users
     *
     * Autoryzacja:
     *
     * @organizationUserSecurity.canManage(authentication, #organizationId)
     *
     * To jest tenant boundary:
     *
     * ORG_ADMIN może zarządzać użytkownikami, ale tylko w swojej organizacji.
     * Nie powinien widzieć ani edytować użytkowników z innych tenantów.
     */
    @GetMapping("/organizations/{organizationId}/users")
    @PreAuthorize("@organizationUserSecurity.canManage(authentication, #organizationId)")
    public List<UserSummaryResponse> getOrganizationUsers(@PathVariable UUID organizationId) {
        return appUserRepository.findByOrganizationId(organizationId).stream()
                .map(this::toUserSummary)
                .toList();
    }

    /**
     * Pobiera listę pracowników dla HR.
     *
     * Endpoint:
     *
     * GET /api/secure/hr/organizations/{organizationId}/employees
     *
     * Autoryzacja:
     *
     * hasRole('HR') and @tenantSecurity.sameOrganization(authentication, #organizationId)
     *
     * To pokazuje łączenie dwóch warunków:
     *
     * 1. użytkownik musi mieć rolę HR,
     * 2. użytkownik musi należeć do tej samej organizacji.
     *
     * Sama rola HR nie wystarcza, bo HR z organizacji A nie powinien widzieć
     * pracowników organizacji B.
     */
    @GetMapping("/hr/organizations/{organizationId}/employees")
    @PreAuthorize("hasRole('HR') and @tenantSecurity.sameOrganization(authentication, #organizationId)")
    public List<UserSummaryResponse> getEmployeesForHr(@PathVariable UUID organizationId) {
        return appUserRepository.findByOrganizationId(organizationId).stream()
                .map(this::toUserSummary)
                .toList();
    }

    /**
     * Pobiera ograniczone podsumowanie płatności dla supportu.
     *
     * Endpoint:
     *
     * GET /api/secure/support/reservations/{reservationId}/payment-summary
     *
     * Autoryzacja:
     *
     * hasRole('SUPPORT') and @reservationSecurity.canView(authentication, #reservationId)
     *
     * SUPPORT może widzieć status operacyjny, ale nie powinien widzieć pełnych
     * danych płatności, np. pełnego numeru karty.
     *
     * To pokazuje ważną zasadę:
     * autoryzacja to nie tylko "czy można wejść na endpoint", ale też
     * "jaki zakres danych wolno zobaczyć".
     */
    @GetMapping("/support/reservations/{reservationId}/payment-summary")
    @PreAuthorize("hasRole('SUPPORT') and @reservationSecurity.canView(authentication, #reservationId)")
    public PaymentSummaryResponse getSupportPaymentSummary(@PathVariable UUID reservationId) {
        return new PaymentSummaryResponse(
                reservationId,
                "VISIBLE_TO_SUPPORT_AS_STATUS_ONLY",
                "**** **** **** ****",
                "Support can inspect operational status, but not full payment data."
        );
    }

    /**
     * Pobiera pełne dane płatności.
     *
     * Endpoint edukacyjny:
     *
     * GET /api/secure/support/reservations/{reservationId}/full-payment-data
     *
     * Autoryzacja:
     *
     * hasRole('ORG_ADMIN') and @reservationSecurity.canView(authentication, #reservationId)
     *
     * Ten endpoint istnieje po to, żeby testy mogły pokazać, że SUPPORT nie ma
     * dostępu do pełnych danych płatności.
     *
     * Uwaga:
     * trzymanie pełnego numeru karty w odpowiedzi API jest tutaj wyłącznie
     * demonstracją edukacyjną. Produkcyjnie nie powinno się wystawiać pełnych
     * danych karty w ten sposób.
     */
    @GetMapping("/support/reservations/{reservationId}/full-payment-data")
    @PreAuthorize("hasRole('ORG_ADMIN') and @reservationSecurity.canView(authentication, #reservationId)")
    public PaymentSummaryResponse getFullPaymentData(@PathVariable UUID reservationId) {
        return new PaymentSummaryResponse(
                reservationId,
                "CAPTURED",
                "4111 1111 1111 1111",
                "Educational endpoint: SUPPORT must not be able to access this."
        );
    }

    /**
     * Prosty endpoint testowy dla uwierzytelnionego użytkownika.
     *
     * Endpoint:
     *
     * GET /api/secure/me
     *
     * Nie ma lokalnego @PreAuthorize, więc dostęp zależy od globalnej konfiguracji
     * Spring Security dla ścieżki /api/secure/**.
     *
     * Przydaje się do sprawdzenia, czy JWT został poprawnie odczytany i request
     * przeszedł przez filtr bezpieczeństwa.
     */
    @GetMapping("/me")
    public SecuredMessageResponse me() {
        return new SecuredMessageResponse("Authenticated request reached a secured endpoint.");
    }

    /**
     * Mapuje encję AppUser na bezpieczne DTO użytkownika.
     *
     * Nie zwracamy encji AppUser bezpośrednio, bo mogłaby zawierać pola techniczne
     * lub wrażliwe, np. hash hasła, refresh tokeny, ustawienia bezpieczeństwa itd.
     */
    private UserSummaryResponse toUserSummary(AppUser user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}