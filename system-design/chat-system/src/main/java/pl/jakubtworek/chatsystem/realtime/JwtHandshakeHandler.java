package pl.jakubtworek.chatsystem.realtime;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Handshake handler odpowiedzialny za przypisanie użytkownika do sesji WebSocket.
 *
 * WebSocketJwtHandshakeInterceptor wcześniej:
 * - odczytuje JWT,
 * - waliduje token,
 * - wyciąga userId i username,
 * - zapisuje je w attributes handshake.
 *
 * Ta klasa bierze te dane z attributes i tworzy Principal.
 *
 * Principal jest później używany m.in. w:
 * - RealtimeMessageController,
 * - WebSocketAuthorizationInterceptor,
 * - WebSocketPresenceEventListener.
 *
 * Najważniejsze:
 * principal.getName() powinno zwracać userId,
 * bo reszta realtime layer traktuje Principal.name jako UUID użytkownika.
 */
@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    /**
     * Metoda wywoływana przez Spring podczas zestawiania połączenia WebSocket.
     *
     * Jej zadaniem jest określenie, kim jest użytkownik tej sesji.
     *
     * Bez poprawnego Principal:
     * - nie wiedzielibyśmy, kto wysyła wiadomości przez WebSocket,
     * - nie moglibyśmy autoryzować subskrypcji do konwersacji,
     * - presence online/offline nie miałoby userId,
     * - prywatne kolejki /user/queue/... nie działałyby poprawnie.
     */
    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        /*
         * userId został wcześniej zapisany przez WebSocketJwtHandshakeInterceptor
         * po poprawnym odczytaniu i walidacji JWT.
         *
         * To jest techniczny identyfikator użytkownika używany w całej aplikacji.
         */
        UUID userId = (UUID) attributes.get(WebSocketJwtHandshakeInterceptor.USER_ID_ATTRIBUTE);

        /*
         * username również pochodzi z JWT.
         *
         * Nie jest kluczowy dla autoryzacji, ale może być przydatny
         * w logach, debugowaniu albo jako dodatkowa informacja w ChatPrincipal.
         */
        String username = (String) attributes.get(WebSocketJwtHandshakeInterceptor.USERNAME_ATTRIBUTE);

        /*
         * Tworzymy Principal przypisany do tej konkretnej sesji WebSocket.
         *
         * Od tego momentu Spring może przekazywać Principal do metod:
         * - @MessageMapping,
         * - interceptorów kanału,
         * - listenerów connect/disconnect.
         *
         * Krytyczne założenie:
         * ChatPrincipal.getName() powinien zwracać userId.toString().
         */
        return new ChatPrincipal(userId, username);
    }
}