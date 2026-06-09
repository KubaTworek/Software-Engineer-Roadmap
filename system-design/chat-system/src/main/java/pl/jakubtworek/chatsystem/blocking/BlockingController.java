package pl.jakubtworek.chatsystem.blocking;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za blokowanie i odblokowywanie użytkowników.
 *
 * Blokady są ważnym elementem bezpieczeństwa i kontroli prywatności w aplikacji czatu.
 *
 * Typowe skutki blokady:
 * - zablokowany użytkownik nie powinien móc utworzyć rozmowy 1:1 z blokującym,
 * - wiadomości między tymi użytkownikami powinny być blokowane,
 * - system może ograniczyć widoczność presence albo profilu,
 * - przyszłe zaproszenia do grup mogą być ograniczane zależnie od polityki produktu.
 *
 * Controller nie implementuje reguł blokowania.
 * Deleguje wszystko do BlockingService.
 */
@RestController
@RequestMapping("/api/blocks")
public class BlockingController {

    /**
     * Serwis zawierający właściwą logikę blokad.
     *
     * To BlockingService powinien decydować:
     * - czy można zablokować danego użytkownika,
     * - czy nie blokujemy samego siebie,
     * - czy blokada już istnieje,
     * - jak blokada wpływa na rozmowy i wysyłkę wiadomości.
     */
    private final BlockingService blockingService;

    public BlockingController(BlockingService blockingService) {
        this.blockingService = blockingService;
    }

    /**
     * Pobiera listę użytkowników zablokowanych przez aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/blocks
     *
     * userId nie jest przekazywany w URL.
     * Bierzemy go z JWT przez @AuthenticationPrincipal.
     *
     * Dzięki temu użytkownik pobiera wyłącznie swoją listę blokad,
     * a nie listę blokad innej osoby.
     */
    @GetMapping
    public List<BlockedUserResponse> getMyBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return blockingService.getMyBlockedUsers(principal.id());
    }

    /**
     * Blokuje wskazanego użytkownika.
     *
     * Endpoint:
     * POST /api/blocks/{userId}
     *
     * principal.id() — użytkownik wykonujący blokadę.
     * userId — użytkownik, który ma zostać zablokowany.
     *
     * BlockingService powinien sprawdzić:
     * - czy target user istnieje,
     * - czy użytkownik nie blokuje samego siebie,
     * - czy blokada już nie istnieje,
     * - czy trzeba dodatkowo ograniczyć istniejące rozmowy.
     *
     * Po tej operacji inne serwisy, np. ConversationService i MessageService,
     * powinny respektować blokadę przez ensureNotBlockedEitherWay().
     */
    @PostMapping("/{userId}")
    public BlockedUserResponse block(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId
    ) {
        return blockingService.block(principal.id(), userId);
    }

    /**
     * Odblokowuje wskazanego użytkownika.
     *
     * Endpoint:
     * DELETE /api/blocks/{userId}
     *
     * Operacja usuwa relację blokady:
     * currentUser -> userId.
     *
     * Jeśli blokada nie istnieje, serwis może:
     * - potraktować operację jako idempotentną i nic nie zrobić,
     * - albo zwrócić błąd, zależnie od przyjętej polityki.
     *
     * Obecny controller nie zwraca body.
     * Sukces oznacza brak wyjątku.
     */
    @DeleteMapping("/{userId}")
    public void unblock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId
    ) {
        blockingService.unblock(principal.id(), userId);
    }
}