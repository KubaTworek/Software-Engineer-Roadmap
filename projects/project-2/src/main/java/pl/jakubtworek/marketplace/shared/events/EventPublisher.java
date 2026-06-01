package pl.jakubtworek.marketplace.shared.events;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

public interface EventPublisher {
    void publish(DomainEvent event);
}
