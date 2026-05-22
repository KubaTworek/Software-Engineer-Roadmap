package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.broker;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestDomainEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake broker used by tests.
 *
 * It can be switched off to simulate temporary Kafka unavailability.
 */
public class FakeKafkaBroker {

    private boolean available = true;
    private final List<TestDomainEvent> publishedEvents = new ArrayList<>();

    /**
     * Publishes an event when the broker is available.
     */
    public void publish(TestDomainEvent event) {
        if (!available) {
            throw new BrokerUnavailableException("Kafka broker is unavailable.");
        }

        publishedEvents.add(event);
    }

    /**
     * Simulates broker outage.
     */
    public void shutdown() {
        this.available = false;
    }

    /**
     * Simulates broker recovery.
     */
    public void start() {
        this.available = true;
    }

    /**
     * Returns all events successfully published to the fake broker.
     */
    public List<TestDomainEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    /**
     * Returns number of successfully published events.
     */
    public int publishedCount() {
        return publishedEvents.size();
    }
}