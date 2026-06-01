package pl.jakubtworek.marketplace.testsupport;

import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestApplicationEventBus implements EventPublisher {
    private final List<DomainEventHandler<?>> handlers = new ArrayList<>();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    public void register(DomainEventHandler<?> handler) {
        handlers.add(handler);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void publish(DomainEvent event) {
        publishedEvents.add(event);
        List<DomainEventHandler<?>> snapshot = List.copyOf(handlers);
        snapshot.stream()
                .filter(handler -> handler.eventType().isAssignableFrom(event.getClass()))
                .forEach(handler -> ((DomainEventHandler) handler).handle(event));
    }

    public List<DomainEvent> events() {
        return Collections.unmodifiableList(publishedEvents);
    }

    public <T extends DomainEvent> List<T> eventsOfType(Class<T> eventType) {
        return publishedEvents.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .toList();
    }
}
