package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitionedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted after payment has been authorized.
 *
 * If this event uses the same orderId key as OrderPlaced, Kafka keeps
 * their ordering within the same partition.
 */
public record PaymentAuthorized(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        String paymentId
) implements PartitionedEvent {

    public static final String TYPE = "PaymentAuthorized";

    @Override
    public String eventType() {
        return TYPE;
    }
}