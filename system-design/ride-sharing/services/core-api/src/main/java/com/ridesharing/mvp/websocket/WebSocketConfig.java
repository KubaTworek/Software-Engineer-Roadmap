package com.ridesharing.mvp.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Konfiguracja WebSocket/STOMP dla aktualizacji real-time.
 *
 * W aplikacji ride-sharing WebSocket służy do szybkiego przekazywania zmian statusu przejazdu:
 * - pasażer widzi, że kierowca został przypisany,
 * - kierowca widzi nowe zdarzenia dotyczące kursu,
 * - obie strony dostają aktualizacje bez ciągłego odpytywania REST API.
 *
 * REST API nadal pozostaje źródłem prawdy.
 * WebSocket jest kanałem powiadomień real-time.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Konfiguruje broker wiadomości STOMP.
     *
     * SimpleBroker działa w pamięci aplikacji.
     * Jest dobry dla MVP i lokalnego developmentu, ale przy wielu instancjach backendu
     * nie zapewni automatycznie współdzielenia wiadomości między nodami.
     *
     * Produkcyjnie, przy real-time gateway cluster, warto użyć zewnętrznego brokera,
     * np. RabbitMQ, ActiveMQ albo osobnej warstwy WebSocket gateway.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * /topic służy do broadcastów/subskrypcji tematycznych.
         * Przykład:
         * /topic/rides/{rideId}
         *
         * /queue służy do kolejek prywatnych, szczególnie w połączeniu z /user.
         * Przykład:
         * /user/queue/rides
         */
        registry.enableSimpleBroker("/topic", "/queue");

        /*
         * Prefix /app oznacza wiadomości wysyłane od klienta do aplikacji.
         *
         * Przykład:
         * klient wysyła do /app/something,
         * a Spring może obsłużyć to metodą z @MessageMapping.
         *
         * W tym projekcie główny flow idzie przez REST,
         * więc WebSocket jest przede wszystkim kanałem server -> client.
         */
        registry.setApplicationDestinationPrefixes("/app");

        /*
         * Prefix /user umożliwia wysyłanie wiadomości do konkretnego użytkownika.
         *
         * RideWebSocketPublisher używa convertAndSendToUser(),
         * więc ta konfiguracja jest potrzebna dla prywatnych kolejek użytkownika.
         */
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Rejestruje endpointy WebSocket, do których łączy się frontend/mobile.
     *
     * Endpoint bazowy:
     * /ws
     *
     * Zarejestrowane są dwie wersje:
     * - z SockJS fallback,
     * - czysty WebSocket.
     *
     * SockJS pomaga klientom/środowiskom, które nie obsługują dobrze natywnego WebSocketa.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        /*
         * Endpoint z SockJS.
         *
         * setAllowedOriginPatterns("*") oznacza, że każdy origin może się połączyć.
         * To wygodne w MVP i developmentcie, ale zbyt szerokie dla produkcji.
         */
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        /*
         * Endpoint bez SockJS, dla klientów obsługujących natywny WebSocket.
         */
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}