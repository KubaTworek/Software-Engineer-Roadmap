package pl.jakubtworek.chatsystem.multiregion;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministyczna symulacja single-writer per conversation. Epoka jest fencing
 * tokenem: stary region nie może zapisać po failoverze, nawet jeśli nadal żyje.
 */
public final class ConversationLeadership {
    public record Lease(String region, long epoch) {}
    private final Map<UUID, Lease> leaders = new ConcurrentHashMap<>();
    public synchronized Lease elect(UUID conversationId, String region) {
        long nextEpoch = Optional.ofNullable(leaders.get(conversationId)).map(Lease::epoch).orElse(0L) + 1;
        Lease lease = new Lease(region, nextEpoch);
        leaders.put(conversationId, lease);
        return lease;
    }
    public synchronized void assertCurrent(UUID conversationId, Lease lease) {
        if (!lease.equals(leaders.get(conversationId))) {
            throw new StaleLeaderException("Leadership epoch is stale");
        }
    }
    public static final class StaleLeaderException extends RuntimeException {
        public StaleLeaderException(String message) { super(message); }
    }
}
