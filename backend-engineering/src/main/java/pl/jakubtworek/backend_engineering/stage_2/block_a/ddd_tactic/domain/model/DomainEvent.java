package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model;

import java.time.Instant;

// Base interface for domain events.
// A domain event represents something important that has already happened.
public interface DomainEvent {

    String eventId();

    Instant occurredAt();
}