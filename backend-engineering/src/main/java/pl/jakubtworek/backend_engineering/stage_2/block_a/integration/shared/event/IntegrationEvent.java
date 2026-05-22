package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event;

import java.time.Instant;

// Base contract for integration events exchanged between bounded contexts.
// Integration events are public contracts and should be versioned carefully.
public interface IntegrationEvent {

    String eventId();

    String eventType();

    int version();

    Instant occurredAt();

    String aggregateId();
}