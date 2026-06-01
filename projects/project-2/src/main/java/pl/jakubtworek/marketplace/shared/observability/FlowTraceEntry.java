package pl.jakubtworek.marketplace.shared.observability;

import java.time.Instant;
import java.util.UUID;

public record FlowTraceEntry(
        Instant timestamp,
        UUID correlationId,
        UUID eventId,
        UUID orderId,
        String component,
        String action,
        String topic,
        String consumerName,
        String message
) {
    public static FlowTraceEntry of(UUID correlationId, UUID eventId, UUID orderId,
                                    String component, String action, String topic, String consumerName, String message) {
        return new FlowTraceEntry(Instant.now(), correlationId, eventId, orderId, component, action, topic, consumerName, message);
    }
}
