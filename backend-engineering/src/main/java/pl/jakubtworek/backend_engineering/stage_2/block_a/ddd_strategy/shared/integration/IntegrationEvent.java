package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.shared.integration;

import java.time.Instant;

// Base class for events published between bounded contexts.
// Integration events should contain only the data required by downstream contexts.
public abstract class IntegrationEvent {

    private final String eventId;
    private final String eventType;
    private final Instant occurredAt;

    protected IntegrationEvent(String eventId, String eventType, Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public String eventId() {
        return eventId;
    }

    public String eventType() {
        return eventType;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}