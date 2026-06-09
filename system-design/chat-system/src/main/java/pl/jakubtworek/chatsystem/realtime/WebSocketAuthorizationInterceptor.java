package pl.jakubtworek.chatsystem.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.conversation.ConversationService;

import java.security.Principal;
import java.util.UUID;

/**
 * Interceptor autoryzujący subskrypcje WebSocket/STOMP.
 *
 * Ta klasa zabezpiecza kanały realtime przed sytuacją, w której użytkownik
 * zna albo zgadnie conversationId i spróbuje subskrybować cudzą rozmowę.
 *
 * Przykład ataku:
 * SUBSCRIBE /topic/conversations/{foreignConversationId}
 *
 * Bez tego interceptora broker mógłby dopuścić subskrypcję,
 * a użytkownik zacząłby odbierać realtime eventy z rozmowy,
 * do której nie powinien mieć dostępu.
 *
 * Najważniejsza zasada:
 * każda subskrypcja topicu konwersacji musi być sprawdzona
 * przez ConversationService.ensureMember().
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

    private final ConversationService conversationService;

    public WebSocketAuthorizationInterceptor(ConversationService conversationService) {
        this.conversationService = conversationService;
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
        Principal principal = accessor.getUser();

        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated WebSocket subscription");
        }

        /*
         * W tej aplikacji principal.getName() przechowuje UUID użytkownika.
         *
         * To musi być spójne z konfiguracją WebSocket authentication,
         * która ustawia Principal podczas nawiązywania połączenia.
         */
        UUID userId = UUID.fromString(principal.getName());

        /*
         * conversationId wyciągamy z końcówki destination.
         *
         * Dla:
         * /topic/conversations/{conversationId}
         *
         * bierzemy fragment po prefixie.
         */
        UUID conversationId = UUID.fromString(
                destination.substring(CONVERSATION_TOPIC_PREFIX.length())
        );

        /*
         * Kluczowe zabezpieczenie.
         *
         * Jeśli użytkownik nie jest członkiem konwersacji,
         * ConversationService rzuci wyjątek i subskrypcja zostanie odrzucona.
         *
         * Dzięki temu użytkownik nie może podsłuchiwać cudzych wiadomości,
         * receiptów, typing indicatorów ani presence eventów.
         */
        conversationService.ensureMember(conversationId, userId);
    }
}