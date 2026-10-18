package pl.jakubtworek.marketplace.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayDeque;
import java.util.UUID;

/** Adapter transportowy utrzymujący jawny lifecycle i ograniczony bufor sesji. */
@Component
public class OrderWebSocketHub {
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ArrayDeque<Notification> journal = new ArrayDeque<>();

    public void connected(WebSocketSession socket, Instant authorizationExpiresAt) {
        connected(socket, authorizationExpiresAt, null, 0);
    }
    public synchronized void connected(WebSocketSession socket, Instant authorizationExpiresAt, long afterSequence) {
        connected(socket, authorizationExpiresAt, null, afterSequence);
    }
    public synchronized void connected(WebSocketSession socket, Instant authorizationExpiresAt, UUID orderId, long afterSequence) {
        clients.put(socket.getId(), new Client(socket, orderId, new ConnectionSession(32, authorizationExpiresAt)));
        journal.stream().filter(it -> it.sequence > afterSequence && (orderId == null || orderId.equals(it.orderId)))
                .forEach(it -> clients.get(socket.getId()).buffer.enqueue(it.payload, Instant.now()));
        flush(clients.get(socket.getId()));
    }
    public void disconnected(String sessionId) { clients.remove(sessionId); }

    public synchronized void orderChanged(String json, Instant now) {
        orderChanged(null, json, now);
    }
    public synchronized void orderChanged(UUID orderId, String json, Instant now) {
        long next = sequence.incrementAndGet();
        String sequenced = "{\"sequence\":" + next + ",\"event\":" + json + "}";
        journal.addLast(new Notification(next, orderId, sequenced));
        if (journal.size() > 256) journal.removeFirst();
        clients.values().stream().filter(client -> client.orderId == null || client.orderId.equals(orderId)).forEach(client -> {
            try {
                client.buffer.enqueue(sequenced, now);
                flush(client);
            } catch (SecurityException expired) {
                close(client.socket);
            }
        });
    }

    private void flush(Client client) {
        if (!client.socket.isOpen()) return;
        while (client.socket.isOpen()) {
            var message = client.buffer.poll();
            if (message.isEmpty()) return;
            try { client.socket.sendMessage(new TextMessage(message.get())); }
            catch (IOException failure) { return; /* klient odczyta trwały stan przez API */ }
        }
    }
    private void close(WebSocketSession socket) {
        clients.remove(socket.getId());
        try { socket.close(); } catch (IOException ignored) { }
    }
    public int connections() { return clients.size(); }
    private record Client(WebSocketSession socket, UUID orderId, ConnectionSession buffer) {}
    private record Notification(long sequence, UUID orderId, String payload) {}
}
