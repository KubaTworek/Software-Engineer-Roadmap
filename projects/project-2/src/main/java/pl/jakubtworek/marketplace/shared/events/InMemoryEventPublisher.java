package pl.jakubtworek.marketplace.shared.events;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.List;

@Component
public class InMemoryEventPublisher implements EventPublisher {
    private final List<DomainEventHandler<?>> handlers;

    public InMemoryEventPublisher(List<DomainEventHandler<?>> handlers) {
        this.handlers = handlers;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        handlers.stream()
                .filter(handler -> handler.eventType().isAssignableFrom(event.getClass()))
                .forEach(handler -> ((DomainEventHandler) handler).handle(event));
    }
}
