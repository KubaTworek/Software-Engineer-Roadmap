package pl.jakubtworek.chatsystem.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.message.MessageRepository;
import pl.jakubtworek.chatsystem.message.MessageResponse;
import pl.jakubtworek.chatsystem.message.MessageStatus;
import pl.jakubtworek.chatsystem.notification.NotificationService;
import pl.jakubtworek.chatsystem.realtime.RealtimeMessagePublisher;

import java.io.IOException;

/**
 * Handler obsługujący event MESSAGE_CREATED.
 *
 * Ten handler jest częścią event-driven flow:
 *
 * 1. MessageService zapisuje wiadomość w bazie.
 * 2. MessageService dopisuje event MESSAGE_CREATED do outboxa.
 * 3. OutboxPublisher przenosi event do kolejki.
 * 4. QueuedEventWorker pobiera event z kolejki.
 * 5. Ten handler wykonuje skutki uboczne:
 *    - publikuje message.created przez WebSocket,
 *    - uruchamia powiadomienia dla użytkowników offline.
 *
 * Dzięki temu wysłanie wiadomości nie musi synchronicznie czekać
 * na WebSocket broadcast ani push notifications.
 *
 * To jest ważne dla skalowalności:
 * zapis wiadomości pozostaje szybki i transakcyjny,
 * a rzeczy poboczne są wykonywane asynchronicznie.
 */
@Component
public class MessageCreatedEventHandler implements DomainEventHandler {

    /**
     * ObjectMapper służy do odczytania JSON payloadu z OutboxEvent.
     *
     * Outbox przechowuje payload jako JSON,
     * a handler musi odtworzyć konkretny typ eventu: MessageCreatedEvent.
     */
    private final ObjectMapper objectMapper;

    /**
     * Repozytorium wiadomości.
     *
     * Event zawiera messageId, ale nie zawiera pełnej encji Message.
     * Dlatego handler dociąga aktualną wiadomość z bazy przed publikacją.
     */
    private final MessageRepository messageRepository;

    /**
     * Publisher WebSocket.
     *
     * Odpowiada za wysłanie eventu message.created do topicu konwersacji:
     * /topic/conversations/{conversationId}
     */
    private final RealtimeMessagePublisher realtimePublisher;

    /**
     * Serwis powiadomień.
     *
     * Po utworzeniu wiadomości sprawdza odbiorców offline
     * i tworzy/wysyła powiadomienia push.
     */
    private final NotificationService notificationService;

    public MessageCreatedEventHandler(
            ObjectMapper objectMapper,
            MessageRepository messageRepository,
            RealtimeMessagePublisher realtimePublisher,
            NotificationService notificationService
    ) {
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.realtimePublisher = realtimePublisher;
        this.notificationService = notificationService;
    }

    /**
     * Informuje worker, że ten handler obsługuje tylko event MESSAGE_CREATED.
     *
     * Dzięki temu QueuedEventWorker może mieć wiele handlerów,
     * ale odpala tylko te, które pasują do event.getEventType().
     */
    @Override
    public boolean supports(String eventType) {
        return EventTypes.MESSAGE_CREATED.equals(eventType);
    }

    /**
     * Obsługuje event MESSAGE_CREATED.
     *
     * Ta metoda nie tworzy wiadomości.
     * Wiadomość już istnieje w bazie.
     *
     * Zadaniem tej metody jest wykonanie działań po fakcie:
     * - realtime delivery,
     * - push notifications dla offline recipients.
     */
    @Override
    @Transactional
    public void handle(OutboxEvent event) {
        /*
         * Odczytujemy payload z JSON zapisany w outbox_events.
         *
         * Payload zawiera m.in. messageId, conversationId, senderId i createdAt.
         */
        MessageCreatedEvent payload = readPayload(event);

        /*
         * Pobieramy wiadomość z bazy po messageId z eventu.
         *
         * Jeżeli wiadomości nie ma, to oznacza niespójność:
         * event mówi o wiadomości, której nie da się znaleźć.
         */
        Message message = messageRepository.findById(payload.messageId())
                .orElseThrow(() -> new NotFoundException("Message not found for outbox event"));

        /*
         * Publikujemy wiadomość realtime do topicu konwersacji.
         *
         * Wszyscy klienci subskrybujący:
         * /topic/conversations/{conversationId}
         *
         * dostaną event:
         * message.created
         *
         * Status SENT jest tutaj wystarczający,
         * bo event oznacza: wiadomość została zapisana i utworzona w systemie.
         * Delivered/read są aktualizowane osobnymi eventami receipt.
         */
        realtimePublisher.publishMessageCreated(
                MessageResponse.from(message, MessageStatus.SENT, null, null)
        );

        /*
         * Uruchamiamy powiadomienia dla odbiorców offline.
         *
         * NotificationService powinien:
         * - pobrać członków konwersacji,
         * - pominąć nadawcę,
         * - sprawdzić presence odbiorców,
         * - uwzględnić ustawienia mute/blokady, jeśli są,
         * - utworzyć albo wysłać powiadomienie.
         *
         * To jest osobny skutek uboczny eventu MESSAGE_CREATED.
         */
        notificationService.notifyOfflineRecipients(message);
    }

    /**
     * Deserializuje JSON payloadu do MessageCreatedEvent.
     *
     * Jeśli payload jest uszkodzony albo niezgodny ze schematem,
     * rzucamy IllegalStateException.
     *
     * Wtedy QueuedEventWorker oznaczy event jako failed,
     * co pozwala później znaleźć problem w outboxie.
     */
    private MessageCreatedEvent readPayload(OutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.getPayloadJson(),
                    MessageCreatedEvent.class
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot deserialize MessageCreatedEvent", ex);
        }
    }
}