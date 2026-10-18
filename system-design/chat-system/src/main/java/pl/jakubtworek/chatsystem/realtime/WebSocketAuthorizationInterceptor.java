package pl.jakubtworek.chatsystem.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.presence.PresenceLeaseService;

import java.security.Principal;
import java.util.UUID;

/**
 * Interceptor autoryzujący subskrypcje WebSocket/STOMP.
 *
 * Ta klasa zabezpiecza kanały realtime przed sytuacją, w której użytkownik
 * zna albo zgadnie conversationId i spróbuje subskrybować dawny publiczny topic.
 *
 * Przykład ataku:
 * SUBSCRIBE /topic/conversations/{foreignConversationId}
 *
 * Bez tego interceptora broker mógłby dopuścić subskrypcję,
 * a użytkownik zacząłby odbierać realtime eventy z rozmowy,
 * do której nie powinien mieć dostępu.
 *
 * Aktualny protokół nie używa publicznych topiców konwersacji. Serwer wykonuje
 * fan-out do prywatnych kolejek {@code /user/queue/conversations/{id}} na podstawie
 * aktualnego membershipu. Stary topic jest zatem zawsze odrzucany fail-closed.
 */
@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    /**
     * Prefix kanału STOMP używanego do eventów konkretnej konwersacji.
     *
     * Klienci subskrybują np.:
     * /topic/conversations/0f4f7d7b-...
     *
     * Interceptor sprawdza tylko te destination,
     * które dotyczą konwersacji.
     */
    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final PresenceLeaseService presenceLeases;

    public WebSocketAuthorizationInterceptor(PresenceLeaseService presenceLeases) {
        this.presenceLeases = presenceLeases;
    }

    /**
     * Metoda wywoływana przed wysłaniem wiadomości STOMP dalej przez kanał Springa.
     *
     * Interesują nas głównie komendy SUBSCRIBE,
     * bo to one decydują, czy użytkownik zacznie odbierać eventy
     * z danego topicu.
     *
     * Dla innych komend, np. SEND, CONNECT, DISCONNECT,
     * ten interceptor nic nie robi.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getUser() != null && accessor.getSessionId() != null) {
            try {
                presenceLeases.heartbeat(accessor.getSessionId(), UUID.fromString(accessor.getUser().getName()));
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Invalid WebSocket principal");
            }
        }

        /*
         * Sprawdzamy tylko próby subskrypcji.
         *
         * SEND do /app/... jest obsługiwany w RealtimeMessageController,
         * gdzie każda operacja i tak przechodzi przez MessageService
         * albo ConversationService.
         */
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    /**
     * Autoryzuje subskrypcję topicu konwersacji.
     *
     * Jeśli destination nie dotyczy konwersacji, metoda kończy działanie.
     * Dzięki temu interceptor nie blokuje innych kanałów,
     * np. prywatnych kolejek /user/queue/messages albo /user/queue/errors.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        /*
         * Interesują nas tylko kanały:
         * /topic/conversations/{conversationId}
         *
         * Inne destination pomijamy.
         */
        if (destination == null || !destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            return;
        }

        /*
         * Principal pochodzi z etapu uwierzytelniania WebSocket,
         * zwykle z JWT przekazanego podczas CONNECT.
         *
         * Brak Principal oznacza, że użytkownik nie jest poprawnie zalogowany
         * w kontekście WebSocket.
         */
        throw new AccessDeniedException(
                "Public conversation topics are disabled; subscribe to the private user queue"
        );
    }
}
