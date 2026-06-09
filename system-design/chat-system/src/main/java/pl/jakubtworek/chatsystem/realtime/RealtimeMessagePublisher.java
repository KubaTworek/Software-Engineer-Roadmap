package pl.jakubtworek.chatsystem.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.message.MessageResponse;
import pl.jakubtworek.chatsystem.message.ReceiptResponse;
import pl.jakubtworek.chatsystem.presence.PresenceResponse;

import java.util.UUID;

/**
 * Komponent odpowiedzialny za publikowanie eventów realtime do klientów WebSocket/STOMP.
 *
 * Ta klasa jest warstwą wyjściową realtime:
 * - nie zapisuje danych w bazie,
 * - nie sprawdza uprawnień,
 * - nie wykonuje logiki biznesowej,
 * - tylko wysyła gotowe eventy na odpowiednie kanały STOMP.
 *
 * Dzięki temu reszta aplikacji nie musi znać szczegółów transportu WebSocket.
 * MessageService, PresenceService albo workery mogą wywołać publisher,
 * a on zajmuje się dostarczeniem eventu do klientów.
 */
@Component
public class RealtimeMessagePublisher {

    /**
     * Główny obiekt Springa do wysyłania wiadomości STOMP.
     *
     * convertAndSend(...) wysyła event na publiczny topic,
     * np. do wszystkich klientów subskrybujących daną konwersację.
     *
     * convertAndSendToUser(...) wysyła event prywatnie do konkretnego użytkownika,
     * np. ACK wysłania wiadomości albo błąd operacji.
     */
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeMessagePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Publikuje nową wiadomość do wszystkich klientów subskrybujących daną konwersację.
     *
     * Destination:
     * /topic/conversations/{conversationId}
     *
     * Event:
     * message.created
     *
     * Ten event informuje członków rozmowy, że pojawiła się nowa wiadomość.
     * Frontend używa go do natychmiastowego dodania wiadomości do widoku czatu.
     */
    public void publishMessageCreated(MessageResponse message) {
        String destination = "/topic/conversations/" + message.conversationId();

        messagingTemplate.convertAndSend(
                destination,
                RealtimeEvent.of("message.created", message)
        );
    }

    /**
     * Publikuje aktualizację statusu wiadomości.
     *
     * Destination:
     * /topic/conversations/{conversationId}
     *
     * Event:
     * message.receipt.updated
     *
     * Ten event obsługuje zmianę statusów:
     * - DELIVERED,
     * - READ.
     *
     * Dzięki temu nadawca może zobaczyć np. zmianę z "sent" na "read"
     * bez odświeżania strony.
     */
    public void publishReceiptUpdated(ReceiptResponse receipt) {
        String destination = "/topic/conversations/" + receipt.conversationId();

        messagingTemplate.convertAndSend(
                destination,
                RealtimeEvent.of("message.receipt.updated", receipt)
        );
    }

    /**
     * Publikuje typing indicator.
     *
     * Destination:
     * /topic/conversations/{conversationId}
     *
     * Event:
     * typing.updated
     *
     * Typing event nie jest trwałym stanem aplikacji.
     * Służy tylko do UX, np. pokazania:
     * "Użytkownik pisze..."
     *
     * Ten event powinien być krótko żyjący po stronie klienta.
     * Jeśli klient nie dostanie typing.stop, frontend i tak powinien wygasić typing
     * po kilku sekundach.
     */
    public void publishTyping(TypingEvent typingEvent) {
        String destination = "/topic/conversations/" + typingEvent.conversationId();

        messagingTemplate.convertAndSend(
                destination,
                RealtimeEvent.of("typing.updated", typingEvent)
        );
    }

    /**
     * Publikuje zmianę presence użytkownika w kontekście konkretnej konwersacji.
     *
     * Destination:
     * /topic/conversations/{conversationId}
     *
     * Event:
     * presence.updated
     *
     * Ten event mówi członkom rozmowy, że użytkownik zmienił status,
     * np. ONLINE/OFFLINE albo zaktualizował lastSeenAt.
     *
     * Presence jest stanem pomocniczym dla UX,
     * nie powinien być traktowany tak krytycznie jak zapis wiadomości.
     */
    public void publishPresence(UUID conversationId, PresenceResponse presence) {
        String destination = "/topic/conversations/" + conversationId;

        messagingTemplate.convertAndSend(
                destination,
                RealtimeEvent.of("presence.updated", presence)
        );
    }

    /**
     * Wysyła prywatne potwierdzenie do nadawcy, że wiadomość została obsłużona przez serwer.
     *
     * Destination po stronie klienta:
     * /user/queue/messages
     *
     * Event:
     * message.sent
     *
     * To nie jest broadcast do całej rozmowy.
     * To jest ACK tylko dla użytkownika, który wysłał wiadomość.
     *
     * Frontend używa tego eventu do:
     * - podmiany lokalnej wiadomości tymczasowej na wiadomość z backendu,
     * - ustawienia statusu "sent",
     * - rozpoznania, czy request był duplikatem po reconnect/retry.
     */
    public void sendMessageSentAck(UUID userId, MessageResponse message, boolean duplicate) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/messages",
                RealtimeEvent.of("message.sent", new MessageSentAck(message, duplicate))
        );
    }

    /**
     * Wysyła prywatny błąd do konkretnego użytkownika.
     *
     * Destination po stronie klienta:
     * /user/queue/errors
     *
     * Event:
     * error
     *
     * Ten mechanizm zastępuje klasyczną odpowiedź HTTP dla WebSocket.
     * Jeśli operacja realtime się nie powiedzie, np.:
     * - użytkownik nie jest członkiem rozmowy,
     * - wiadomość nie przejdzie moderacji,
     * - request jest niepoprawny,
     * - wystąpi wyjątek biznesowy,
     *
     * klient dostaje błąd na prywatnej kolejce i może pokazać odpowiedni komunikat w UI.
     */
    public void sendError(UUID userId, String code, String message) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                RealtimeEvent.of("error", new WsError(code, message))
        );
    }

    /**
     * Payload błędu wysyłanego do klienta przez WebSocket.
     *
     * code — techniczny kod błędu, np. WS_MESSAGE_ERROR.
     * message — opis błędu możliwy do pokazania lub zalogowania po stronie frontendu.
     */
    public record WsError(String code, String message) {}
}