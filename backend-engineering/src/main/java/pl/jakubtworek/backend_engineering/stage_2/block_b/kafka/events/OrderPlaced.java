package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitionedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted after an order has been placed.
 *
 * This event should be published with orderId as the Kafka message key.
 */
public record OrderPlaced(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        BigDecimal totalAmount
) implements PartitionedEvent {

    public static final String TYPE = "OrderPlaced";

    @Override
    public String eventType() {
        return TYPE;
    }
}