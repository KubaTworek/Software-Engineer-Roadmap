package pl.jakubtworek.marketplace.testsupport;

import tools.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestApplicationEventBus implements EventPublisher {
    private final List<DomainEventHandler<?>> handlers = new ArrayList<>();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();
    private final OutboxEventMapper contractBoundary = new OutboxEventMapper(new ObjectMapper());

    public void register(DomainEventHandler<?> handler) {
        handlers.add(handler);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void publish(DomainEvent event) {
        publishedEvents.add(event);
        /*
         * Ten synchroniczny bus jest wyłącznie adapterem testowym. Każdy fakt
         * przechodzi przez tę samą serializację i normalizację kontraktu co outbox
         * oraz Kafka. Bez tego test omijałby najważniejszą granicę modułów i
         * przypadkowo utrwalał zależności od cudzych klas domenowych.
         */
        DomainEvent publishedLanguageEvent = contractBoundary.toDomainEvent(
                contractBoundary.toOutboxEvent(event));
        List<DomainEventHandler<?>> snapshot = List.copyOf(handlers);
        snapshot.stream()
                .filter(handler -> handler.eventType().isAssignableFrom(publishedLanguageEvent.getClass()))
                .forEach(handler -> ((DomainEventHandler) handler).handle(publishedLanguageEvent));
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
