package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** A slow client is rejected explicitly instead of consuming unbounded heap. */
public final class BoundedSessionBuffer {

    private final int capacity;
    private final Deque<StreamEvent> pending = new ArrayDeque<>();

    public BoundedSessionBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized OfferResult offer(StreamEvent event) {
        if (pending.size() == capacity) {
            return OfferResult.SLOW_CONSUMER;
        }
        pending.addLast(event);
        return OfferResult.ACCEPTED;
    }

    public synchronized void acknowledgeThrough(long sequence) {
        while (!pending.isEmpty() && pending.getFirst().sequence() <= sequence) {
            pending.removeFirst();
        }
    }

    public synchronized List<StreamEvent> pending() {
        return List.copyOf(pending);
    }

    public enum OfferResult {
        ACCEPTED,
        SLOW_CONSUMER
    }
}
