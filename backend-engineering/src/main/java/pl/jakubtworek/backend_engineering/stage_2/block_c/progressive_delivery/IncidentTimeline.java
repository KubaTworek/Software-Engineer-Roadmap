package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only, chronological evidence used during an incident and postmortem. */
public final class IncidentTimeline {

    public enum EventType {
        DETECTED,
        DECLARED,
        MITIGATION_STARTED,
        CHANGE_APPLIED,
        RECOVERED
    }

    public record Event(Instant at, EventType type, String evidence) {}

    private final List<Event> events = new ArrayList<>();

    public synchronized void append(Instant at, EventType type, String evidence) {
        if (at == null || type == null || evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("complete timeline evidence is required");
        }
        if (!events.isEmpty() && at.isBefore(events.getLast().at())) {
            throw new IllegalArgumentException("timeline must be chronological");
        }
        events.add(new Event(at, type, evidence));
    }

    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    public synchronized Duration timeToRecovery() {
        Instant detected = first(EventType.DETECTED);
        Instant recovered = first(EventType.RECOVERED);
        return Duration.between(detected, recovered);
    }

    private Instant first(EventType type) {
        return events.stream().filter(event -> event.type() == type).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing timeline event: " + type)).at();
    }
}
