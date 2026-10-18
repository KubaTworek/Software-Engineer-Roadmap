package pl.jakubtworek.marketplace.realtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Framework-neutralny model lifecycle połączenia WebSocket.
 *
 * Długie połączenie wymaga ponownej autoryzacji po wygaśnięciu tokenu oraz jawnej
 * polityki backpressure. Nieskończona kolejka dla wolnego klienta kończy się OOM.
 * Tutaj najstarsze niekrytyczne powiadomienie jest odrzucane, a licznik dropped
 * pozwala operatorowi zobaczyć degradację. Dane źródłowe nadal pobiera się przez
 * API — WebSocket jest sygnałem zmiany, a nie źródłem prawdy.
 */
public final class ConnectionSession {
    private final int capacity;
    private final Instant authorizationExpiresAt;
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private long dropped;

    public ConnectionSession(int capacity, Instant authorizationExpiresAt) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.authorizationExpiresAt = authorizationExpiresAt;
    }

    public void enqueue(String notification, Instant now) {
        if (!now.isBefore(authorizationExpiresAt)) throw new SecurityException("connection authorization expired");
        if (pending.size() == capacity) { pending.removeFirst(); dropped++; }
        pending.addLast(notification);
    }

    public Optional<String> poll() { return Optional.ofNullable(pending.pollFirst()); }
    public long droppedMessages() { return dropped; }
}
