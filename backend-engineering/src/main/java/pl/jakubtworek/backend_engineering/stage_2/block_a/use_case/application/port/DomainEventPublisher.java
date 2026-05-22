package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.DomainEvent;

// Port used to publish domain events.
// A production implementation may use the transactional outbox pattern.
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}