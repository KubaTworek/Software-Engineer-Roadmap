package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestDomainEvent;

import java.util.UUID;

/**
 * Single row in the outbox table.
 *
 * The event remains in the outbox until it is successfully published to Kafka.
 */
public record OutboxEntry(
        UUID outboxId,
        TestDomainEvent event,
        boolean published
) {
    /**
     * Returns a copy of this entry marked as published.
     */
    public OutboxEntry markPublished() {
        return new OutboxEntry(outboxId, event, true);
    }
}