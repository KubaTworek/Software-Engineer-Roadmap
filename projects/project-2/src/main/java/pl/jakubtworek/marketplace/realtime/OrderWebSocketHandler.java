package pl.jakubtworek.marketplace.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import pl.jakubtworek.marketplace.security.OrderOwnership;

/**
 * WebSocket jest kanałem server-to-client. Klient nie może tym kanałem zmieniać
 * zamówienia, co zapobiega obchodzeniu walidacji i idempotencji API komend.
 */
@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {
    private final OrderWebSocketHub hub;
    private final OrderOwnership ownership;
    public OrderWebSocketHandler(OrderWebSocketHub hub, OrderOwnership ownership) { this.hub = hub; this.ownership = ownership; }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        // W laboratorium TTL sesji jest krótki i jawny; produkcja bierze go z uwierzytelnionego tokenu.
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        long afterSequence = parseLong(query, "after", 0);
        UUID orderId = parseUuid(query, "orderId");
        if (session.getPrincipal() instanceof Authentication auth && !ownership.canRead(orderId, auth)) {
            throw new SecurityException("caller cannot subscribe to this order");
        }
        hub.connected(session, Instant.now().plusSeconds(300), orderId, afterSequence);
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.disconnected(session.getId());
    }

    private long parseLong(String query, String name, long fallback) {
        if (query == null) return fallback;
        for (String parameter : query.split("&")) {
            if (parameter.startsWith(name + "=")) {
                try { return Math.max(0, Long.parseLong(parameter.substring(name.length() + 1))); }
                catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " must be a number"); }
            }
        }
        return fallback;
    }
    private UUID parseUuid(String query, String name) {
        if (query != null) for (String parameter : query.split("&")) {
            if (parameter.startsWith(name + "=")) return UUID.fromString(parameter.substring(name.length() + 1));
        }
        throw new IllegalArgumentException(name + " is required");
    }
}
