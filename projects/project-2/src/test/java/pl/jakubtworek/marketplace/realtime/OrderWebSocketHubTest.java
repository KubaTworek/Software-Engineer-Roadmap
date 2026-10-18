package pl.jakubtworek.marketplace.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;

class OrderWebSocketHubTest {
    @Test void sendsSignalThroughActualWebSocketSessionAndDropsExpiredConnection() throws Exception {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("client-1"); when(socket.isOpen()).thenReturn(true);
        var hub = new OrderWebSocketHub();
        hub.connected(socket, Instant.parse("2026-01-01T00:01:00Z"));
        hub.orderChanged("{\"type\":\"ORDER_CONFIRMED\"}", Instant.parse("2026-01-01T00:00:00Z"));
        verify(socket).sendMessage(argThat((org.springframework.web.socket.WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("ORDER_CONFIRMED")));

        hub.orderChanged("late", Instant.parse("2026-01-01T00:02:00Z"));
        verify(socket).close();
    }

    @Test void resumesOnlyEventsForSubscribedOrderAfterKnownSequence() throws Exception {
        var hub = new OrderWebSocketHub();
        UUID wanted = UUID.randomUUID();
        hub.orderChanged(wanted, "{\"type\":\"ONE\"}", Instant.parse("2026-01-01T00:00:00Z"));
        hub.orderChanged(UUID.randomUUID(), "{\"type\":\"OTHER\"}", Instant.parse("2026-01-01T00:00:01Z"));
        hub.orderChanged(wanted, "{\"type\":\"THREE\"}", Instant.parse("2026-01-01T00:00:02Z"));
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("resuming"); when(socket.isOpen()).thenReturn(true);

        hub.connected(socket, Instant.now().plusSeconds(60), wanted, 1);

        verify(socket).sendMessage(argThat((org.springframework.web.socket.WebSocketMessage<?> message) ->
                message.getPayload().toString().contains("THREE") && message.getPayload().toString().contains("sequence")));
    }
}
