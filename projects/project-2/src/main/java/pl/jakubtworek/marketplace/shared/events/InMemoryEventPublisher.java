package pl.jakubtworek.marketplace.shared.events;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.List;

/**
 * Backward-compatible name kept for early tests and examples.
 * Prefer ApplicationEventBus for the phase-2 implementation.
 */
@Deprecated
public class InMemoryEventPublisher implements EventPublisher {
    private final ApplicationEventBus delegate;

    public InMemoryEventPublisher(List<DomainEventHandler<?>> handlers) {
        this.delegate = new ApplicationEventBus(handlers);
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publish(event);
    }
}
