package pl.jakubtworek.chatsystem.realtime.flow;

import java.util.*;

/**
 * Model kolejki pojedynczego połączenia. Zdarzeń trwałych nie wolno po cichu
 * zgubić: przepełnienie wymusza reconnect i catch-up z bazy. Zdarzenia efemeryczne
 * (typing/presence) można koaleskować, bo kolejny stan zastępuje poprzedni.
 */
public final class BoundedRealtimeBuffer {
    public enum Priority { DURABLE, EPHEMERAL }
    public enum Result { ACCEPTED, COALESCED, RECONNECT_REQUIRED }
    public record Frame(String key, String payload, Priority priority) {}

    private final int capacity;
    private final Deque<Frame> frames = new ArrayDeque<>();

    public BoundedRealtimeBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized Result offer(Frame frame) {
        if (frame.priority() == Priority.EPHEMERAL) {
            boolean replaced = frames.removeIf(existing -> existing.priority() == Priority.EPHEMERAL
                    && existing.key().equals(frame.key()));
            if (frames.size() == capacity) {
                // Najstarszy efemeryczny stan jest bezpiecznym kandydatem do usunięcia.
                Optional<Frame> discardable = frames.stream()
                        .filter(existing -> existing.priority() == Priority.EPHEMERAL).findFirst();
                if (discardable.isPresent()) frames.remove(discardable.get());
                else return Result.RECONNECT_REQUIRED;
            }
            frames.addLast(frame);
            return replaced ? Result.COALESCED : Result.ACCEPTED;
        }
        if (frames.size() == capacity) return Result.RECONNECT_REQUIRED;
        frames.addLast(frame);
        return Result.ACCEPTED;
    }

    public synchronized List<Frame> drain(int limit) {
        List<Frame> result = new ArrayList<>();
        while (result.size() < limit && !frames.isEmpty()) result.add(frames.removeFirst());
        return List.copyOf(result);
    }

    public synchronized int size() { return frames.size(); }
}
