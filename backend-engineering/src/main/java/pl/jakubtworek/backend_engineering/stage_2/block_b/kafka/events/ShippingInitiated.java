package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.events;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.PartitionedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when shipping has been initiated.
 *
 * It belongs to the same order workflow and should therefore use orderId
 * as its Kafka key.
 */
public record ShippingInitiated(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        String shipmentId
) implements PartitionedEvent {

    public static final String TYPE = "ShippingInitiated";

    @Override
    public String eventType() {
        return TYPE;
    }
}