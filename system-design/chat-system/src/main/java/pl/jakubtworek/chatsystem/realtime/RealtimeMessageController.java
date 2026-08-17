package pl.jakubtworek.chatsystem.realtime;

import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.message.MessageService;
import pl.jakubtworek.chatsystem.message.SendMessageRequest;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Controller obsługujący wiadomości przychodzące przez WebSocket/STOMP.
 *
 * To jest realtime'owy odpowiednik REST controllerów dla wiadomości.
 * Klient nie wywołuje tutaj klasycznego endpointu HTTP,
 * tylko wysyła event STOMP na kanały typu:
 *
 * - /app/messages.send
 * - /app/messages.delivered
 * - /app/messages.read
 * - /app/typing.start
 * - /app/typing.stop
 *
 * Kluczowa odpowiedzialność tej klasy:
 * - odebrać event z WebSocket,
 * - ustalić zalogowanego użytkownika z Principal,
 * - przekazać logikę biznesową do MessageService / ConversationService,
 * - opublikować odpowiedni event realtime przez RealtimeMessagePublisher.
 *
 * Ta klasa nie zapisuje bezpośrednio wiadomości, receiptów ani typing state.
 * Jest warstwą transportową dla komunikacji realtime.
 */
@Controller
@Validated
public class RealtimeMessageController {

    /**
     * Serwis wiadomości.
     *
     * Odpowiada za właściwą logikę:
     * - zapis wiadomości,
     * - deduplikację po clientMessageId,
     * - receipts delivered/read,
     * - walidację członkostwa,
     * - blokady,
     * - moderację,
     * - outbox/eventy.
     */
    private final MessageService messageService;

    /**
     * Serwis konwersacji.
     *
     * W tej klasie używany głównie do sprawdzenia,
     * czy użytkownik może wysłać typing event do danej rozmowy.
     */
    private final ConversationService conversationService;

    /**
     * Publisher odpowiedzialny za wysyłanie eventów do klientów WebSocket.
     *
     * Controller nie powinien znać szczegółów kanałów STOMP.
     * Robi to publisher, np.:
     * - ACK do konkretnego użytkownika,
     * - event do wszystkich członków konwersacji,
     * - typing indicator,
     * - błędy prywatne dla nadawcy.
     */
    private final RealtimeMessagePublisher publisher;

    public RealtimeMessageController(
            MessageService messageService,
            ConversationService conversationService,
            RealtimeMessagePublisher publisher
    ) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.publisher = publisher;
    }

    /**
     * Obsługuje wysyłanie wiadomości przez WebSocket.
     *
     * Klient wysyła event:
     * SEND /app/messages.send
     *
     * Najważniejsze:
     * - principal.getName() zawiera id aktualnie zalogowanego użytkownika,
     * - conversationId pochodzi z payloadu WebSocket,
     * - clientMessageId pozwala uniknąć duplikatów przy retry/reconnect,
     * - MessageService wykonuje właściwy zapis i walidację,
     * - po zapisie wysyłamy prywatny ACK do nadawcy.
     *
     * Ten endpoint nie publikuje bezpośrednio message.created do rozmowy.
     * W dobrze rozdzielonej architekturze broadcast do odbiorców może iść
     * przez outbox/event bus po trwałym zapisie wiadomości.
     */
    @MessageMapping("/messages.send")
    public void sendMessage(@Valid @Payload WsSendMessageRequest request, Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());

        /*
         * Zamiana requestu WebSocketowego na standardowy SendMessageRequest.
         * Dzięki temu REST i WebSocket mogą używać tej samej logiki w MessageService.
         */
        var result = messageService.sendMessageWithMetadata(
                currentUserId,
                request.conversationId(),
                new SendMessageRequest(
                        request.clientMessageId(),
                        request.body(),
                        request.attachmentIds()
                )
        );

        /*
         * ACK trafia tylko do nadawcy.
         *
         * Informuje klienta:
         * - wiadomość została zapisana,
         * - serwer nadał jej finalne id i timestamp,
         * - czy request był duplikatem.
         *
         * To pozwala frontendowi zmienić lokalny status z "sending" na "sent".
         */
        publisher.sendMessageSentAck(currentUserId, result.message(), result.duplicate());
    }

    /**
     * Obsługuje potwierdzenie dostarczenia wiadomości przez WebSocket.
     *
     * Klient wysyła event:
     * SEND /app/messages.delivered
     *
     * Semantyka:
     * - aplikacja klienta potwierdza, że odebrała wiadomość,
     * - backend oznacza jako DELIVERED wszystkie wiadomości do wskazanego messageId,
     * - następnie publikuje event receipt.updated do zainteresowanych klientów.
     *
     * DELIVERED nie oznacza jeszcze, że użytkownik przeczytał wiadomość.
     */
    @MessageMapping("/messages.delivered")
    public void markDelivered(@Valid @Payload WsReceiptRequest request, Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());

        var receipt = messageService.markDeliveredUpTo(
                currentUserId,
                request.conversationId(),
                request.messageId()
        );

        /*
         * Informujemy realtime'owo pozostałych klientów,
         * że status wiadomości został zaktualizowany.
         */
        publisher.publishReceiptUpdated(receipt);
    }

    /**
     * Obsługuje potwierdzenie przeczytania wiadomości przez WebSocket.
     *
     * Klient wysyła event:
     * SEND /app/messages.read
     *
     * Semantyka:
     * - użytkownik faktycznie zobaczył wiadomość,
     * - backend oznacza jako READ wiadomości do wskazanego messageId,
     * - aktualizowany jest też lastReadAt członka konwersacji,
     * - unread count dla tej rozmowy powinien się zmniejszyć lub wyzerować.
     *
     * READ jest mocniejszym statusem niż DELIVERED.
     */
    @MessageMapping("/messages.read")
    public void markRead(@Valid @Payload WsReceiptRequest request, Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());

        var receipt = messageService.markReadUpTo(
                currentUserId,
                request.conversationId(),
                request.messageId()
        );

        /*
         * Publikujemy zmianę statusu do klientów,
         * żeby UI mogło zaktualizować checkmarki / status wiadomości.
         */
        publisher.publishReceiptUpdated(receipt);
    }

    /**
     * Obsługuje rozpoczęcie pisania przez użytkownika.
     *
     * Klient wysyła event:
     * SEND /app/typing.start
     *
     * Typing indicator jest stanem nietrwałym.
     * Nie zapisujemy go w bazie.
     *
     * Najważniejsze zabezpieczenie:
     * zanim opublikujemy typing event, sprawdzamy,
     * czy użytkownik faktycznie należy do konwersacji.
     */
    @MessageMapping("/typing.start")
    public void typingStarted(@Valid @Payload WsTypingRequest request, Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());

        /*
         * Bez tego użytkownik mógłby wysyłać typing eventy
         * do rozmów, do których nie ma dostępu.
         */
        conversationService.ensureMember(request.conversationId(), currentUserId);

        /*
         * Publikujemy informację do członków konwersacji:
         * "ten użytkownik zaczął pisać".
         */
        publisher.publishTyping(
                new TypingEvent(
                        request.conversationId(),
                        currentUserId,
                        true,
                        Instant.now()
                )
        );
    }

    /**
     * Obsługuje zakończenie pisania przez użytkownika.
     *
     * Klient wysyła event:
     * SEND /app/typing.stop
     *
     * Tak jak typing.start, ten event jest tylko realtime'ową informacją dla UI.
     * Nie powinien być trwałym stanem biznesowym.
     */
    @MessageMapping("/typing.stop")
    public void typingStopped(@Valid @Payload WsTypingRequest request, Principal principal) {
        UUID currentUserId = UUID.fromString(principal.getName());

        /*
         * Także przy typing.stop sprawdzamy membership,
         * bo każdy event dotyczący rozmowy musi być autoryzowany.
         */
        conversationService.ensureMember(request.conversationId(), currentUserId);

        /*
         * Publikujemy informację do członków konwersacji:
         * "ten użytkownik przestał pisać".
         */
        publisher.publishTyping(
                new TypingEvent(
                        request.conversationId(),
                        currentUserId,
                        false,
                        Instant.now()
                )
        );
    }

    /**
     * Centralna obsługa błędów dla metod @MessageMapping w tym controllerze.
     *
     * Jeżeli podczas obsługi eventu WebSocket wystąpi wyjątek,
     * nie zwracamy klasycznej odpowiedzi HTTP.
     *
     * Zamiast tego wysyłamy prywatny event błędu do użytkownika,
     * np. na kolejkę /user/queue/errors.
     *
     * Dzięki temu frontend może pokazać użytkownikowi,
     * że wysłanie wiadomości, receipt albo typing event się nie udały.
     */
    @MessageExceptionHandler
    public void handleException(RuntimeException exception, Principal principal) {
        if (principal != null) {
            publisher.sendError(
                    UUID.fromString(principal.getName()),
                    "WS_MESSAGE_ERROR",
                    exception.getMessage()
            );
        }
    }
}