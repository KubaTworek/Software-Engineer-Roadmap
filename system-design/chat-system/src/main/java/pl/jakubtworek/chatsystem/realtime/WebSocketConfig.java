package pl.jakubtworek.chatsystem.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Główna konfiguracja WebSocket/STOMP dla aplikacji czatu.
 *
 * Ta klasa odpowiada za:
 * - wystawienie endpointów WebSocket,
 * - podpięcie JWT podczas handshake,
 * - ustawienie Principal użytkownika,
 * - konfigurację kanałów STOMP,
 * - konfigurację brokera wiadomości,
 * - obsługę prywatnych kolejek użytkownika,
 * - heartbeat,
 * - pule wątków dla ruchu przychodzącego i wychodzącego,
 * - autoryzację subskrypcji do topiców konwersacji.
 *
 * To jest centralny punkt konfiguracji realtime.
 * Sama logika wysyłania wiadomości jest w RealtimeMessageController,
 * a samo publikowanie eventów w RealtimeMessagePublisher.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Liczba bazowych wątków obsługujących wiadomości przychodzące od klientów.
     *
     * Ruch inbound to np.:
     * - SEND /app/messages.send,
     * - SEND /app/messages.read,
     * - SEND /app/typing.start.
     */
    private final int inboundCorePoolSize;

    /**
     * Maksymalna liczba wątków dla ruchu przychodzącego.
     *
     * Pozwala obsłużyć chwilowe skoki ruchu,
     * np. wielu użytkowników wysyła wiadomości jednocześnie.
     */
    private final int inboundMaxPoolSize;

    /**
     * Liczba bazowych wątków obsługujących wiadomości wychodzące do klientów.
     *
     * Ruch outbound to np.:
     * - message.created,
     * - message.receipt.updated,
     * - typing.updated,
     * - presence.updated.
     */
    private final int outboundCorePoolSize;

    /**
     * Maksymalna liczba wątków dla wysyłania eventów do klientów.
     *
     * Ważne przy większej liczbie subskrybentów,
     * bo jedna wiadomość może być rozesłana do wielu użytkowników.
     */
    private final int outboundMaxPoolSize;

    /**
     * Interceptor handshake.
     *
     * Odpowiada za wyciągnięcie JWT z requestu WebSocket,
     * np. z query parametru albo nagłówka,
     * i przygotowanie danych potrzebnych do uwierzytelnienia połączenia.
     */
    private final WebSocketJwtHandshakeInterceptor handshakeInterceptor;

    /**
     * Handshake handler.
     *
     * Ustawia Principal użytkownika dla połączenia WebSocket.
     *
     * To jest krytyczne, bo później principal.getName()
     * jest używane jako UUID użytkownika w controllerach realtime
     * i przy autoryzacji subskrypcji.
     */
    private final JwtHandshakeHandler handshakeHandler;

    /**
     * Interceptor autoryzujący subskrypcje STOMP.
     *
     * Sprawdza, czy użytkownik może subskrybować:
     * /topic/conversations/{conversationId}
     *
     * Bez tego użytkownik mógłby próbować podsłuchiwać cudze rozmowy,
     * jeśli znałby albo zgadł conversationId.
     */
    private final WebSocketAuthorizationInterceptor authorizationInterceptor;

    public WebSocketConfig(
            WebSocketJwtHandshakeInterceptor handshakeInterceptor,
            JwtHandshakeHandler handshakeHandler,
            WebSocketAuthorizationInterceptor authorizationInterceptor,
            @Value("${app.websocket.inbound-core-pool-size:4}") int inboundCorePoolSize,
            @Value("${app.websocket.inbound-max-pool-size:16}") int inboundMaxPoolSize,
            @Value("${app.websocket.outbound-core-pool-size:4}") int outboundCorePoolSize,
            @Value("${app.websocket.outbound-max-pool-size:16}") int outboundMaxPoolSize
    ) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.authorizationInterceptor = authorizationInterceptor;
        this.inboundCorePoolSize = inboundCorePoolSize;
        this.inboundMaxPoolSize = inboundMaxPoolSize;
        this.outboundCorePoolSize = outboundCorePoolSize;
        this.outboundMaxPoolSize = outboundMaxPoolSize;
    }

    /**
     * Rejestruje endpointy WebSocket dostępne dla klientów.
     *
     * Klient łączy się z jednym z tych endpointów,
     * a potem komunikuje się już przez STOMP:
     * - SEND /app/...
     * - SUBSCRIBE /topic/...
     * - SUBSCRIBE /user/queue/...
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        /*
         * Główny endpoint WebSocket.
         *
         * Przykład połączenia:
         * ws://localhost:8080/ws?token=JWT
         *
         * Używany przez nowoczesne klienty,
         * które wspierają natywny WebSocket.
         */
        registry.addEndpoint("/ws")
                /*
                 * Dopuszcza połączenia z różnych originów.
                 *
                 * Dobre do developmentu.
                 * W produkcji lepiej zawęzić to do konkretnych domen frontendu,
                 * np. https://app.example.com.
                 */
                .setAllowedOriginPatterns("*")

                /*
                 * Interceptor pobiera i waliduje dane JWT podczas handshake.
                 */
                .addInterceptors(handshakeInterceptor)

                /*
                 * Handler ustawia Principal użytkownika dla połączenia WebSocket.
                 */
                .setHandshakeHandler(handshakeHandler);

        /*
         * Alternatywny endpoint z SockJS.
         *
         * SockJS daje fallback dla środowisk,
         * gdzie natywny WebSocket może być niedostępny albo blokowany.
         *
         * W praktyce przy nowoczesnym frontendzie często wystarczy /ws,
         * ale /ws-sockjs ułatwia kompatybilność.
         */
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
    }

    /**
     * Konfiguruje routing wiadomości STOMP i broker.
     *
     * Tutaj definiujemy:
     * - które destination trafiają do aplikacji,
     * - które destination obsługuje broker,
     * - jak działają prywatne kolejki użytkowników,
     * - heartbeat połączenia.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        /*
         * Prefix wiadomości kierowanych do metod @MessageMapping.
         *
         * Przykład:
         * klient wysyła SEND /app/messages.send
         * Spring kieruje to do:
         * @MessageMapping("/messages.send")
         */
        registry.setApplicationDestinationPrefixes("/app");

        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.initialize();
        /*
         * Włącza prosty broker pamięciowy dla kanałów:
         * - /topic — broadcast do wielu subskrybentów,
         * - /queue — kolejki, często prywatne.
         *
         * W tym projekcie:
         * /topic/conversations/{id} służy do eventów rozmowy,
         * np. message.created, typing.updated, presence.updated.
         *
         * Przy dużej skali ten simple broker warto zastąpić brokerem zewnętrznym,
         * np. RabbitMQ, ActiveMQ albo relay do infrastruktury messagingowej.
         */
        registry.enableSimpleBroker("/topic", "/queue")
                /*
                 * Heartbeat co 10 sekund w obie strony.
                 *
                 * Pomaga wykrywać martwe połączenia,
                 * np. zamkniętą kartę, utratę internetu albo zerwany socket.
                 */
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler);

        /*
         * Prefix dla prywatnych wiadomości do użytkownika.
         *
         * Backend wysyła przez:
         * convertAndSendToUser(userId, "/queue/messages", ...)
         *
         * Klient subskrybuje:
         * /user/queue/messages
         * /user/queue/errors
         */
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Konfiguruje kanał przychodzący od klientów do aplikacji.
     *
     * Przez ten kanał przechodzą m.in.:
     * - CONNECT,
     * - SUBSCRIBE,
     * - SEND /app/messages.send,
     * - SEND /app/messages.read,
     * - SEND /app/typing.start.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                /*
                 * Bazowa liczba wątków dla obsługi ruchu przychodzącego.
                 */
                .corePoolSize(inboundCorePoolSize)

                /*
                 * Maksymalna liczba wątków dla burstów ruchu.
                 */
                .maxPoolSize(inboundMaxPoolSize)

                /*
                 * Kolejka buforująca wiadomości przychodzące.
                 *
                 * Chroni system przed krótkimi pikami,
                 * ale zbyt duża kolejka może maskować przeciążenie.
                 */
                .queueCapacity(10_000);

        /*
         * Podpinamy autoryzację subskrypcji.
         *
         * Dzięki temu próba:
         * SUBSCRIBE /topic/conversations/{conversationId}
         *
         * zostanie sprawdzona pod kątem członkostwa użytkownika w rozmowie.
         */
        registration.interceptors(authorizationInterceptor);
    }

    /**
     * Konfiguruje kanał wychodzący z aplikacji do klientów.
     *
     * Przez ten kanał przechodzą eventy wysyłane do przeglądarek/aplikacji:
     * - message.created,
     * - message.sent,
     * - message.receipt.updated,
     * - typing.updated,
     * - presence.updated,
     * - error.
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                /*
                 * Bazowa liczba wątków odpowiedzialnych za wysyłanie eventów do klientów.
                 */
                .corePoolSize(outboundCorePoolSize)

                /*
                 * Maksymalna liczba wątków outbound.
                 *
                 * Przy wielu aktywnych subskrypcjach outbound może być większym bottleneckiem
                 * niż inbound, bo jeden event może trafić do wielu klientów.
                 */
                .maxPoolSize(outboundMaxPoolSize)

                /*
                 * Większa kolejka outbound,
                 * bo broadcast eventów do klientów może chwilowo generować dużo pracy.
                 */
                .queueCapacity(20_000);
    }
}