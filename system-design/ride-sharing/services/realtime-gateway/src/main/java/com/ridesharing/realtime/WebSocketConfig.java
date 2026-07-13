package com.ridesharing.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Konfiguracja WebSocket/STOMP dla Realtime Gateway.
 *
 * W aplikacji ride-sharing ta warstwa odpowiada za realtime komunikację z klientami:
 * - pasażer widzi zmianę statusu przejazdu,
 * - kierowca dostaje ofertę lub update przejazdu,
 * - panel operacyjny widzi live eventy miasta,
 * - frontend może subskrybować kanały zamiast odpytywać REST co sekundę.
 *
 * WebSocket nie jest źródłem prawdy.
 * Źródłem prawdy pozostają serwisy domenowe, baza/Redis i eventy w Kafce.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Konfiguruje broker wiadomości STOMP.
     *
     * W tej wersji używany jest simple broker wbudowany w aplikację.
     * To działa dobrze lokalnie i w MVP, ale nie jest wystarczające dla klastra wielu instancji.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * Prefixy obsługiwane przez broker.
         *
         * /topic:
         * - broadcast / pub-sub,
         * - np. /topic/rides/{rideId},
         * - np. /topic/cities/{cityId}/locations.
         *
         * /queue:
         * - wiadomości punkt-punkt,
         * - często używane razem z /user.
         */
        registry.enableSimpleBroker(
                "/topic",
                "/queue"
        );

        /*
         * Prefix dla wiadomości wysyłanych od klienta do aplikacji.
         *
         * Przykład:
         * klient wysyła do /app/something,
         * a Spring routuje to do metody @MessageMapping.
         *
         * W tej klasie nie ma jeszcze @MessageMapping,
         * więc obecnie realtime działa głównie jako server -> client fanout.
         */
        registry.setApplicationDestinationPrefixes("/app");

        /*
         * Prefix dla wiadomości użytkownika.
         *
         * Pozwala używać convertAndSendToUser(...),
         * np. /user/queue/rides dla konkretnego pasażera albo kierowcy.
         *
         * To jest bezpieczniejsze niż publiczne /topic/rides/{rideId},
         * jeśli event dotyczy konkretnego użytkownika.
         */
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Rejestruje endpoint WebSocket/STOMP.
     *
     * Klient łączy się z:
     * /ws
     *
     * withSockJS() dodaje fallback dla środowisk, gdzie czysty WebSocket
     * może nie być dostępny.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")

                /*
                 * Pozwala na połączenia z dowolnego origin.
                 *
                 * To jest wygodne w development,
                 * ale ryzykowne w produkcji.
                 *
                 * Produkcyjnie należy ograniczyć origins do konkretnych domen,
                 * np. aplikacji pasażera, aplikacji kierowcy i panelu admina.
                 */
                .setAllowedOriginPatterns("*")

                /*
                 * SockJS fallback.
                 *
                 * Pomaga w starszych klientach/proxy,
                 * ale zwiększa powierzchnię konfiguracji CORS/session.
                 */
                .withSockJS();
    }
}