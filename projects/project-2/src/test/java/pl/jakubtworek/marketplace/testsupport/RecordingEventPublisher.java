package pl.jakubtworek.marketplace.testsupport;

import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingEventPublisher implements EventPublisher {
    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        events.add(event);
    }

    public List<DomainEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public <T extends DomainEvent> List<T> eventsOfType(Class<T> eventType) {
        return events.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
    }
}
