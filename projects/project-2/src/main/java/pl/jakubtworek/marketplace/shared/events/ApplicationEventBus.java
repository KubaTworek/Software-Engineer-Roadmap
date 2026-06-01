package pl.jakubtworek.marketplace.shared.events;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.List;

/**
 * Synchronous application-level event bus used inside the modular monolith.
 *
 * This is intentionally not Kafka/RabbitMQ yet. It allows modules to react to domain events
 * without direct calls to other modules' application services or infrastructure details.
 */
@Component
public class ApplicationEventBus implements EventPublisher {
    private final List<DomainEventHandler<?>> handlers;

    public ApplicationEventBus(List<DomainEventHandler<?>> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        handlers.stream()
                .filter(handler -> handler.eventType().isAssignableFrom(event.getClass()))
                .forEach(handler -> ((DomainEventHandler) handler).handle(event));
    }
}
