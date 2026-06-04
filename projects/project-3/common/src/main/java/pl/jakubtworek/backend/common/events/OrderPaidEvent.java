package pl.jakubtworek.backend.common.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaidEvent(
        UUID eventId,
        UUID orderId,
        UUID reservationId,
        String userId,
        BigDecimal amount,
        Instant occurredAt,
        String correlationId,
        String requestId,
        String traceId
) implements Serializable {
    public static OrderPaidEvent now(UUID eventId, UUID orderId, UUID reservationId, String userId, BigDecimal amount,
                                     String correlationId, String requestId, String traceId) {
        return new OrderPaidEvent(eventId, orderId, reservationId, userId, amount, Instant.now(), correlationId, requestId, traceId);
    }
}
