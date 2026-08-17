package com.example.videostreaming.premium;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.auth.UserRepository;
import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.geo.GeoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.premium.PremiumDtos.*;

/**
 * Kontroler subskrypcji i entitlementów premium.
 *
 * Główna odpowiedzialność:
 * - zwraca aktualną subskrypcję zalogowanego użytkownika,
 * - pozwala sprawdzić, czy użytkownik ma dostęp do konkretnego filmu,
 * - pozwala adminowi ręcznie utworzyć subskrypcję dla użytkownika.
 *
 * Ważne:
 * Ten kontroler nie obsługuje płatności.
 * W MVP subskrypcje są tworzone administracyjnie.
 * Produkcyjnie createSubscription powinno być zwykle wywoływane
 * przez billing/payment workflow, np. po potwierdzeniu płatności.
 */
@RestController
@RequestMapping("/api/premium")
public class SubscriptionController {

    /**
     * Repozytorium użytkowników.
     *
     * Używane przy administracyjnym tworzeniu subskrypcji,
     * żeby sprawdzić, czy wskazany userId istnieje.
     */
    private final UserRepository users;

    /**
     * Repozytorium subskrypcji użytkowników.
     *
     * Przechowuje aktywne i historyczne subskrypcje,
     * np. FREE, BASIC, PREMIUM z datą wygaśnięcia.
     */
    private final UserSubscriptionRepository subscriptions;

    /**
     * Repozytorium filmów.
     *
     * Używane przy sprawdzaniu entitlementu dla konkretnego videoId.
     */
    private final VideoRepository videos;

    /**
     * Serwis decyzji dostępu.
     *
     * Centralizuje logikę:
     * - aktualny plan użytkownika,
     * - minimalny plan wymagany przez film,
     * - wynik geo-blockingu,
     * - informację o DRM.
     */
    private final EntitlementService entitlements;

    /**
     * Serwis geo-blockingu.
     *
     * Sprawdza, czy film jest dostępny w kraju przekazanym w requestcie.
     */
    private final GeoService geoService;

    public SubscriptionController(UserRepository users,
                                  UserSubscriptionRepository subscriptions,
                                  VideoRepository videos,
                                  EntitlementService entitlements,
                                  GeoService geoService) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.videos = videos;
        this.entitlements = entitlements;
        this.geoService = geoService;
    }

    /**
     * Zwraca najlepszą aktywną subskrypcję aktualnego użytkownika.
     *
     * Endpoint dla profilu użytkownika / ustawień konta.
     *
     * Jeśli użytkownik nie ma aktywnej subskrypcji, zwracane jest null.
     * W samej logice entitlementów taki użytkownik jest traktowany jako FREE.
     *
     * Uwaga:
     * Zwracanie null jest proste dla MVP, ale w API produkcyjnym czytelniejsze
     * byłoby zwrócenie jawnego planu FREE albo obiektu z active=false.
     */
    @GetMapping("/me/subscription")
    public SubscriptionResponse mySubscription(@AuthenticationPrincipal User user) {
        return subscriptions.findBestActiveForUser(user.getId(), java.time.Instant.now())
                .map(SubscriptionResponse::from)
                .orElse(null);
    }

    /**
     * Sprawdza, czy aktualny użytkownik ma dostęp do konkretnego filmu.
     *
     * Endpoint diagnostyczny i pomocniczy dla klienta.
     * PlaybackController wykonuje podobny check przed faktycznym wydaniem URL-i.
     *
     * Flow:
     * 1. Pobiera film.
     * 2. Sprawdza geo-blocking dla kraju z nagłówka X-Geo-Country.
     * 3. Pyta EntitlementService o decyzję.
     * 4. Zwraca wynik: allowed, reason, userPlan, requiredPlan, drmRequired, geoAllowed.
     *
     * Uwaga:
     * Tutaj kraj jest pobierany bezpośrednio z @RequestHeader.
     * W PlaybackController lepsze jest użycie GeoService.resolveCountry(request),
     * bo obsługuje też nagłówki CDN typu CloudFront-Viewer-Country i CF-IPCountry.
     */
    @GetMapping("/entitlements/videos/{videoId}")
    public EntitlementResponse checkEntitlement(@AuthenticationPrincipal User user,
                                                @PathVariable UUID videoId,
                                                @RequestHeader(value = "X-Geo-Country", required = false) String country) {
        Video video = videos.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Video not found"
                ));

        /*
         * Sprawdzamy, czy kraj z requestu jest dozwolony dla filmu.
         *
         * Jeśli film nie ma ograniczeń regionalnych, geoAllowed będzie true.
         * Jeśli ma ograniczenia, country musi być obecne i znajdować się
         * na liście allowedCountries filmu.
         */
        boolean geoAllowed = geoService.isAllowed(video, country);

        /*
         * Centralna decyzja dostępu.
         *
         * EntitlementService bierze pod uwagę:
         * - plan użytkownika,
         * - plan wymagany przez film,
         * - wynik geo-blockingu,
         * - czy film wymaga DRM.
         */
        var decision = entitlements.check(user, video, geoAllowed);

        return new EntitlementResponse(
                user.getId(),
                video.getId(),
                decision.allowed(),
                decision.reason(),
                decision.userPlan(),
                decision.requiredPlan(),
                decision.drmRequired(),
                decision.geoAllowed()
        );
    }

    /**
     * Tworzy subskrypcję użytkownika.
     *
     * Dostęp:
     * Tylko ADMIN.
     *
     * W MVP to prosty sposób nadania użytkownikowi planu premium.
     * Produkcyjnie ten endpoint powinien być zastąpiony lub uzupełniony
     * przez integrację z systemem płatności i webhookami billingowymi.
     *
     * Flow:
     * 1. Admin podaje userId, planCode i expiresAt.
     * 2. System sprawdza, czy użytkownik istnieje.
     * 3. System zapisuje nową subskrypcję.
     * 4. API zwraca zapisany plan.
     */
    @PostMapping("/admin/subscriptions")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        User user = users.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        UserSubscription saved = subscriptions.save(
                new UserSubscription(
                        user,
                        request.planCode(),
                        request.expiresAt()
                )
        );

        return SubscriptionResponse.from(saved);
    }
}