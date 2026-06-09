package pl.jakubtworek.chatsystem.message;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za operacje na wiadomościach w ramach konkretnej konwersacji.
 *
 * Ten controller nie zawiera logiki biznesowej — jego zadaniem jest:
 * - odebranie requestu HTTP,
 * - pobranie zalogowanego użytkownika z kontekstu bezpieczeństwa,
 * - pobranie conversationId z URL,
 * - walidacja request body,
 * - przekazanie operacji do MessageService.
 *
 * Właściwe reguły aplikacji, np. sprawdzanie członkostwa w konwersacji,
 * deduplikacja wiadomości, paginacja, unread count czy statusy read/delivered,
 * powinny znajdować się w MessageService.
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    /**
     * Wstrzyknięcie serwisu obsługującego logikę wiadomości.
     *
     * Controller zależy od MessageService, ale sam nie wie,
     * czy wiadomości są zapisywane w PostgreSQL, osobnym message store,
     * czy publikowane przez outbox/event bus.
     */
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Wysyła nową wiadomość do konkretnej konwersacji.
     *
     * Endpoint:
     * POST /api/conversations/{conversationId}/messages
     *
     * Kluczowe rzeczy:
     * - principal.id() identyfikuje nadawcę wiadomości,
     * - conversationId określa rozmowę, do której wiadomość ma trafić,
     * - @Valid uruchamia walidację SendMessageRequest,
     * - MessageService powinien sprawdzić, czy użytkownik jest członkiem tej konwersacji,
     * - MessageService powinien obsłużyć clientMessageId, żeby retry requestu
     *   nie tworzył duplikatów wiadomości.
     *
     * Zwracany MessageResponse reprezentuje wiadomość zapisaną po stronie serwera,
     * zwykle ze statusem SENT.
     */
    @PostMapping
    public MessageResponse sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return messageService.sendMessage(principal.id(), conversationId, request);
    }

    /**
     * Pobiera historię wiadomości z danej konwersacji z paginacją wsteczną.
     *
     * Endpoint:
     * GET /api/conversations/{conversationId}/messages?before=...&limit=50
     *
     * Parametr before:
     * - opcjonalny,
     * - oznacza: pobierz wiadomości starsze niż podany timestamp,
     * - gdy go nie ma, pobierane są najnowsze wiadomości.
     *
     * Parametr limit:
     * - ogranicza liczbę zwracanych wiadomości,
     * - domyślnie 50,
     * - realnie MessageService powinien dodatkowo narzucić maksymalny limit,
     *   np. 100, żeby klient nie mógł pobrać zbyt dużej porcji danych.
     *
     * MessagePageResponse powinien zawierać wiadomości oraz cursor/hasMore,
     * dzięki czemu frontend może implementować infinite scroll historii czatu.
     */
    @GetMapping
    public MessagePageResponse getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return messageService.getMessages(principal.id(), conversationId, before, limit);
    }

    /**
     * Pobiera wiadomości utworzone po wskazanym czasie.
     *
     * Endpoint:
     * GET /api/conversations/{conversationId}/messages/since?after=...&limit=100
     *
     * Ten endpoint jest ważny dla obsługi reconnectu i trybu offline.
     *
     * Typowy scenariusz:
     * - klient traci połączenie WebSocket,
     * - po ponownym połączeniu wysyła ostatni znany timestamp,
     * - backend zwraca wiadomości, które pojawiły się od tego momentu.
     *
     * Dzięki temu WebSocket nie musi być jedynym źródłem prawdy.
     * Jeżeli klient przegapi event realtime, może zsynchronizować brakujące dane przez REST.
     */
    @GetMapping("/since")
    public List<MessageResponse> getMessagesSince(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant after,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return messageService.getMessagesSince(principal.id(), conversationId, after, limit);
    }

    /**
     * Oznacza wiadomości jako dostarczone do aktualnego użytkownika.
     *
     * Endpoint:
     * POST /api/conversations/{conversationId}/messages/delivered
     *
     * Semantyka:
     * - klient informuje backend, że otrzymał wiadomość o danym messageId,
     * - MessageService aktualizuje receipt użytkownika w tej konwersacji,
     * - zwykle oznacza to "dostarczono wszystkie wiadomości do messageId włącznie".
     *
     * To nie oznacza jeszcze, że użytkownik przeczytał wiadomość.
     * To tylko techniczne potwierdzenie dostarczenia do klienta/aplikacji.
     */
    @PostMapping("/delivered")
    public ReceiptResponse markDelivered(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ReceiptRequest request
    ) {
        return messageService.markDeliveredUpTo(principal.id(), conversationId, request.messageId());
    }

    /**
     * Oznacza wiadomości jako przeczytane przez aktualnego użytkownika.
     *
     * Endpoint:
     * POST /api/conversations/{conversationId}/messages/read
     *
     * Semantyka:
     * - klient wywołuje ten endpoint, gdy użytkownik faktycznie zobaczył wiadomość,
     * - MessageService aktualizuje read receipt,
     * - unread count dla tej konwersacji powinien zostać zmniejszony lub wyzerowany
     *   zgodnie z ostatnią przeczytaną wiadomością.
     *
     * Read receipt jest silniejszym statusem niż delivered.
     * W praktyce wiadomość przeczytana powinna być też traktowana jako dostarczona.
     */
    @PostMapping("/read")
    public ReceiptResponse markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ReceiptRequest request
    ) {
        return messageService.markReadUpTo(principal.id(), conversationId, request.messageId());
    }
}