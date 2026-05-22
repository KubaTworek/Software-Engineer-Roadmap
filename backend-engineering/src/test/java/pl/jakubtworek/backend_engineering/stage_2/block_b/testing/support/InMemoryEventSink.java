package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestDomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * In-memory event sink used by tests to capture published events.
 *
 * This avoids using a real Kafka broker in unit tests while still allowing
 * assertions about emitted events.
 */
public class InMemoryEventSink {

    private final List<TestDomainEvent> events = new ArrayList<>();

    /**
     * Stores a published event in memory.
     */
    public void publish(TestDomainEvent event) {
        events.add(event);
    }

    /**
     * Returns all captured events.
     */
    public List<TestDomainEvent> allEvents() {
        return List.copyOf(events);
    }

    /**
     * Counts captured events matching a predicate.
     */
    public long count(Predicate<TestDomainEvent> predicate) {
        return events.stream()
                .filter(predicate)
                .count();
    }

    /**
     * Checks whether at least one matching event was published.
     */
    public boolean contains(Predicate<TestDomainEvent> predicate) {
        return events.stream().anyMatch(predicate);
    }

    /**
     * Clears all captured events between tests.
     */
    public void clear() {
        events.clear();
    }
}