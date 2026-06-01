package pl.jakubtworek.marketplace.shared.events;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

public interface DomainEventHandler<T extends DomainEvent> {
    Class<T> eventType();
    void handle(T event);
}
