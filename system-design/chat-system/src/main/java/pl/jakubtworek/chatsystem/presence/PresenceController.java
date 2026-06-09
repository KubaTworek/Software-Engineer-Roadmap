package pl.jakubtworek.chatsystem.presence;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za presence użytkowników.
 *
 * Presence oznacza aktualny stan dostępności użytkownika, np.:
 * - ONLINE,
 * - OFFLINE,
 * - lastSeenAt.
 *
 * W aplikacji czatu presence jest używane głównie do UX:
 * - pokazania zielonej kropki online,
 * - pokazania "ostatnio widziany",
 * - informowania innych członków rozmowy o zmianie statusu.
 *
 * Automatyczna aktualizacja presence odbywa się przez WebSocketPresenceEventListener,
 * który reaguje na connect/disconnect WebSocket.
 *
 * Ten controller daje dodatkowe endpointy REST do:
 * - sprawdzenia własnego presence,
 * - ręcznego ustawienia online/offline,
 * - sprawdzenia presence innego użytkownika.
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    /**
     * Serwis zawierający właściwą logikę presence.
     *
     * Controller nie powinien sam przechowywać statusów ani wyliczać lastSeenAt.
     * To robi PresenceService.
     */
    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /**
     * Pobiera presence aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/presence/me
     *
     * Użycie:
     * - frontend może sprawdzić, jaki status backend aktualnie widzi dla użytkownika,
     * - przydatne po odświeżeniu strony albo starcie aplikacji.
     *
     * principal.id() pochodzi z JWT i identyfikuje zalogowanego użytkownika.
     */
    @GetMapping("/me")
    public PresenceResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return presenceService.getPresence(principal.id());
    }

    /**
     * Ręcznie oznacza aktualnego użytkownika jako ONLINE.
     *
     * Endpoint:
     * POST /api/presence/me/online
     *
     * W normalnym flow status ONLINE jest ustawiany automatycznie
     * po zestawieniu połączenia WebSocket.
     *
     * Ten endpoint może być przydatny jako fallback,
     * np. dla klienta, który nie używa WebSocket albo chce ręcznie odświeżyć presence.
     */
    @PostMapping("/me/online")
    public PresenceResponse markMeOnline(@AuthenticationPrincipal UserPrincipal principal) {
        return presenceService.markOnline(principal.id());
    }

    /**
     * Ręcznie oznacza aktualnego użytkownika jako OFFLINE.
     *
     * Endpoint:
     * POST /api/presence/me/offline
     *
     * W normalnym flow status OFFLINE jest ustawiany automatycznie,
     * gdy użytkownik straci ostatnie aktywne połączenie WebSocket.
     *
     * Ten endpoint może być użyty np. przy logout,
     * zamknięciu sesji albo jako fallback dla klientów bez WebSocket.
     */
    @PostMapping("/me/offline")
    public PresenceResponse markMeOffline(@AuthenticationPrincipal UserPrincipal principal) {
        return presenceService.markOffline(principal.id());
    }

    /**
     * Pobiera presence wskazanego użytkownika.
     *
     * Endpoint:
     * GET /api/presence/users/{userId}
     *
     * Użycie:
     * - frontend może sprawdzić, czy rozmówca jest online,
     * - można pokazać lastSeenAt na profilu albo w nagłówku rozmowy.
     *
     * Ważna uwaga:
     * w obecnej wersji endpoint nie sprawdza relacji między użytkownikami.
     * W bardziej restrykcyjnej aplikacji warto dodać kontrolę prywatności,
     * np. pozwalać na odczyt presence tylko znajomym albo członkom wspólnej konwersacji.
     */
    @GetMapping("/users/{userId}")
    public PresenceResponse getUserPresence(@PathVariable UUID userId) {
        return presenceService.getPresence(userId);
    }
}