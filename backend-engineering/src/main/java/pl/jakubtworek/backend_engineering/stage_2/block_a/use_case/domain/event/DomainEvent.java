package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event;

import java.time.Instant;

// Base interface for domain events.
// A domain event represents a business fact that has already happened.
public interface DomainEvent {

    String eventId();

    Instant occurredAt();

    String eventType();
}