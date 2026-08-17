package pl.jakubtworek.chatsystem.realtime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import pl.jakubtworek.chatsystem.auth.JwtService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interceptor wykonywany podczas WebSocket handshake.
 *
 * Jego zadaniem jest uwierzytelnienie klienta zanim połączenie WebSocket
 * zostanie zaakceptowane przez backend.
 *
 * W klasycznym REST API JWT jest sprawdzany przez filtr Spring Security.
 * Przy WebSocket trzeba obsłużyć to osobno na etapie handshake,
 * bo późniejsza komunikacja odbywa się już po stałym połączeniu.
 *
 * Jeśli token jest poprawny:
 * - wyciągamy userId,
 * - wyciągamy username,
 * - zapisujemy je w attributes sesji WebSocket,
 * - pozwalamy na zestawienie połączenia.
 *
 * Jeśli tokenu nie ma albo jest błędny:
 * - handshake zostaje odrzucony,
 * - klient nie uzyskuje połączenia realtime.
 */
@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * Nazwa atrybutu sesji WebSocket, pod którym zapisujemy UUID użytkownika.
     *
     * Ten atrybut jest później używany przez JwtHandshakeHandler
     * do zbudowania Principal dla połączenia.
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    /**
     * Nazwa atrybutu sesji WebSocket, pod którym zapisujemy username.
     *
     * Username może być przydatny diagnostycznie albo w logach,
     * ale najważniejszym identyfikatorem technicznym pozostaje userId.
     */
    public static final String USERNAME_ATTRIBUTE = "username";

    /**
     * Serwis odpowiedzialny za parsowanie i walidację JWT.
     *
     * Ta klasa nie powinna samodzielnie znać struktury tokenu.
     * Deleguje to do JwtService.
     */
    private final JwtService jwtService;

    public WebSocketJwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Metoda wykonywana przed zaakceptowaniem WebSocket handshake.
     *
     * Zwraca:
     * - true, jeśli połączenie może zostać zaakceptowane,
     * - false, jeśli połączenie powinno zostać odrzucone.
     *
     * To jest pierwsza linia obrony dla realtime.
     * Bez poprawnego JWT użytkownik nie powinien móc otworzyć WebSocket.
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        /*
         * Token może przyjść:
         * - w nagłówku Authorization: Bearer <token>,
         * - albo jako query param ?token=<token>.
         *
         * Query param jest praktyczny dla WebSocketów,
         * bo część klientów przeglądarkowych ma ograniczone możliwości
         * ustawiania custom headers podczas handshake.
         */
        String token = extractToken(request);

        /*
         * Brak tokenu oznacza brak autoryzacji połączenia.
         * Nie tworzymy anonimowych sesji WebSocket.
         */
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            /*
             * Jeśli token jest poprawny, wyciągamy dane użytkownika.
             *
             * jwtService powinien tutaj zweryfikować m.in.:
             * - podpis tokenu,
             * - datę wygaśnięcia,
             * - poprawność claims.
             */
            UUID userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);

            /*
             * Dane zapisane w attributes będą dostępne później
             * w procesie tworzenia Principal dla tej sesji WebSocket.
             *
             * Dzięki temu kolejne klasy mogą używać principal.getName()
             * jako identyfikatora użytkownika.
             */
            attributes.put(USER_ID_ATTRIBUTE, userId);
            attributes.put(USERNAME_ATTRIBUTE, username);

            return true;
        } catch (RuntimeException ex) {
            /*
             * Każdy błąd parsowania albo walidacji tokenu oznacza odrzucenie handshake.
             *
             * Celowo nie przepuszczamy szczegółów błędu do klienta,
             * żeby nie ułatwiać debugowania atakującemu.
             */
            return false;
        }
    }

    /**
     * Metoda wykonywana po zakończeniu handshake.
     *
     * Tutaj nie musimy nic robić, bo cała potrzebna logika
     * dzieje się przed zaakceptowaniem połączenia.
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    /**
     * Wyciąga JWT z requestu WebSocket handshake.
     *
     * Preferowana kolejność:
     * 1. Authorization: Bearer <token>
     * 2. Query parameter: ?token=<token>
     *
     * Header Authorization jest czystszy z punktu widzenia bezpieczeństwa,
     * ale query param bywa konieczny w praktycznych klientach WebSocket/SockJS.
     */
    private String extractToken(ServerHttpRequest request) {

        /*
         * Najpierw próbujemy pobrać token z nagłówka Authorization.
         *
         * Format:
         * Authorization: Bearer eyJhbGciOi...
         */
        List<String> authorizationHeaders =
                request.getHeaders().get(HttpHeaders.AUTHORIZATION);

        if (authorizationHeaders != null) {
            for (String header : authorizationHeaders) {
                if (header != null && header.startsWith("Bearer ")) {
                    return header.substring(7);
                }
            }
        }

        /*
         * Fallback: token w query stringu.
         *
         * Przykład:
         * ws://localhost:8080/ws?token=eyJhbGciOi...
         *
         * Uwaga produkcyjna:
         * token w URL może trafić do logów reverse proxy albo historii narzędzi.
         * W produkcji warto preferować Authorization header,
         * a query param stosować świadomie.
         */
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
    }
}