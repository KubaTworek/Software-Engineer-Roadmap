package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za zarządzanie konwersacjami.
 *
 * Obsługuje:
 * - tworzenie rozmów 1:1,
 * - tworzenie grup,
 * - pobieranie listy konwersacji użytkownika,
 * - pobieranie szczegółów jednej konwersacji,
 * - dodawanie członków do grup,
 * - zmianę ról członków,
 * - usuwanie członków z grup.
 *
 * Controller nie zawiera logiki biznesowej.
 * Jego rola to:
 * - przyjąć request HTTP,
 * - pobrać aktualnego użytkownika z JWT,
 * - pobrać parametry z URL/body,
 * - uruchomić walidację DTO przez @Valid,
 * - przekazać operację do ConversationService.
 *
 * Reguły typu:
 * - czy użytkownik jest członkiem rozmowy,
 * - czy ma rolę OWNER/ADMIN,
 * - czy można dodać danego użytkownika,
 * - czy rozmowa 1:1 już istnieje,
 * - czy użytkownicy się nie blokują,
 * powinny znajdować się w ConversationService.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    /**
     * Serwis zawierający właściwą logikę konwersacji.
     *
     * Controller nie powinien bezpośrednio operować na repozytoriach,
     * bo wtedy logika uprawnień i reguły domenowe rozlałyby się po warstwie HTTP.
     */
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Tworzy albo zwraca istniejącą rozmowę 1:1 między aktualnym użytkownikiem
     * a użytkownikiem wskazanym w request body.
     *
     * Endpoint:
     * POST /api/conversations/direct
     *
     * Najważniejsze:
     * - userId aktualnego użytkownika pochodzi z JWT,
     * - drugi uczestnik pochodzi z CreateDirectConversationRequest,
     * - ConversationService powinien dopilnować, żeby nie tworzyć duplikatów rozmów 1:1,
     * - ConversationService powinien sprawdzić blokady między użytkownikami.
     */
    @PostMapping("/direct")
    public ConversationResponse createDirectConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        return conversationService.createDirectConversation(principal.id(), request);
    }

    /**
     * Tworzy nową konwersację grupową.
     *
     * Endpoint:
     * POST /api/conversations/groups
     *
     * Aktualny użytkownik zwykle staje się OWNER grupy.
     * Pozostali użytkownicy z requestu są dodawani jako MEMBER.
     *
     * ConversationService powinien sprawdzić:
     * - czy podani użytkownicy istnieją,
     * - czy lista członków jest poprawna,
     * - czy twórca nie próbuje dodać niedozwolonych użytkowników,
     * - czy nie ma konfliktów wynikających z blokad.
     */
    @PostMapping("/groups")
    public ConversationResponse createGroupConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupConversationRequest request
    ) {
        return conversationService.createGroupConversation(principal.id(), request);
    }

    /**
     * Pobiera listę konwersacji aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/conversations
     *
     * To jest jeden z kluczowych endpointów dla ekranu inbox/listy rozmów.
     *
     * Odpowiedź powinna zawierać dane potrzebne do UI, np.:
     * - conversationId,
     * - typ rozmowy DIRECT/GROUP,
     * - tytuł albo nazwę rozmówcy,
     * - ostatnią wiadomość,
     * - unreadCount,
     * - rolę użytkownika w grupie,
     * - timestamp ostatniej aktywności.
     *
     * Serwis powinien zwracać tylko konwersacje,
     * których aktualny użytkownik jest członkiem.
     */
    @GetMapping
    public List<ConversationResponse> myConversations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return conversationService.getMyConversations(principal.id());
    }

    /**
     * Pobiera szczegóły jednej konwersacji.
     *
     * Endpoint:
     * GET /api/conversations/{conversationId}
     *
     * Najważniejsze zabezpieczenie:
     * ConversationService musi sprawdzić,
     * czy aktualny użytkownik jest członkiem tej konwersacji.
     *
     * Bez tego użytkownik mógłby próbować odczytać cudzą rozmowę,
     * znając albo zgadując conversationId.
     */
    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId
    ) {
        return conversationService.getConversation(principal.id(), conversationId);
    }

    /**
     * Dodaje użytkownika do konwersacji grupowej.
     *
     * Endpoint:
     * POST /api/conversations/{conversationId}/members
     *
     * Ta operacja powinna być dostępna tylko dla odpowiednich ról,
     * najczęściej OWNER albo ADMIN.
     *
     * ConversationService powinien sprawdzić:
     * - czy konwersacja istnieje,
     * - czy jest typu GROUP,
     * - czy aktualny użytkownik ma uprawnienia do dodawania członków,
     * - czy dodawany użytkownik istnieje,
     * - czy nie jest już członkiem,
     * - czy nie ma blokady między użytkownikami.
     */
    @PostMapping("/{conversationId}/members")
    public ConversationResponse addMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AddGroupMemberRequest request
    ) {
        return conversationService.addMember(principal.id(), conversationId, request);
    }

    /**
     * Zmienia rolę członka grupy.
     *
     * Endpoint:
     * PATCH /api/conversations/{conversationId}/members/{userId}/role
     *
     * Role mogą obejmować np.:
     * - OWNER,
     * - ADMIN,
     * - MEMBER.
     *
     * To jest operacja administracyjna.
     * ConversationService powinien pilnować:
     * - kto może zmieniać role,
     * - czy nie odbieramy ostatniego OWNER-a,
     * - czy użytkownik docelowy należy do grupy,
     * - czy nie próbujemy zmieniać ról w rozmowie DIRECT.
     */
    @PatchMapping("/{conversationId}/members/{userId}/role")
    public ConversationResponse updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return conversationService.updateMemberRole(
                principal.id(),
                conversationId,
                userId,
                request
        );
    }

    /**
     * Usuwa użytkownika z grupy.
     *
     * Endpoint:
     * DELETE /api/conversations/{conversationId}/members/{userId}
     *
     * Typowe przypadki:
     * - admin usuwa członka grupy,
     * - owner usuwa admina/membera,
     * - użytkownik opuszcza grupę, jeśli userId == principal.id().
     *
     * ConversationService powinien obsłużyć reguły:
     * - tylko grupy wspierają zarządzanie członkami,
     * - nie można usunąć użytkownika z rozmowy DIRECT,
     * - nie można usunąć ostatniego OWNER-a,
     * - zwykły MEMBER nie powinien usuwać innych członków.
     */
    @DeleteMapping("/{conversationId}/members/{userId}")
    public ConversationResponse removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID userId
    ) {
        return conversationService.removeMember(
                principal.id(),
                conversationId,
                userId
        );
    }
}