package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitionedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when payment has failed.
 *
 * This event should also be keyed by orderId so that compensation logic
 * observes events in the expected order.
 */
public record PaymentFailed(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        String reason
) implements PartitionedEvent {

    public static final String TYPE = "PaymentFailed";

    @Override
    public String eventType() {
        return TYPE;
    }
}