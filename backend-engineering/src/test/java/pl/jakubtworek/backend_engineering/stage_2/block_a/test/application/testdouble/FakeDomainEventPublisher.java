package pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;

// Test double for DomainEventPublisher.
// It stores published events in memory instead of sending them to a broker.
public final class FakeDomainEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        events.add(event);
    }

    public List<DomainEvent> publishedEvents() {
        return List.copyOf(events);
    }

    public int publishCount() {
        return events.size();
    }
}