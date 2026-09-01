package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Retained sequence log used by a reconnecting client to resume without a silent gap. */
public final class ResumableEventLog {

    private final int retention;
    private final Deque<StreamEvent> events = new ArrayDeque<>();
    private long nextSequence = 1;

    public ResumableEventLog(int retention) {
        if (retention < 1) {
            throw new IllegalArgumentException("retention must be positive");
        }
        this.retention = retention;
    }

    public synchronized StreamEvent append(String payload) {
        StreamEvent event = new StreamEvent(nextSequence++, payload);
        events.addLast(event);
        while (events.size() > retention) {
            events.removeFirst();
        }
        return event;
    }

    public synchronized List<StreamEvent> replayAfter(long lastSeenSequence) {
        long oldestAvailable = events.isEmpty() ? nextSequence : events.getFirst().sequence();
        if (lastSeenSequence < oldestAvailable - 1) {
            throw new ResumeWindowExceededException(
                    "last seen " + lastSeenSequence + " is older than replay window " + oldestAvailable);
        }
        List<StreamEvent> replay = new ArrayList<>();
        events.stream()
                .filter(event -> event.sequence() > lastSeenSequence)
                .forEach(replay::add);
        return List.copyOf(replay);
    }
}
