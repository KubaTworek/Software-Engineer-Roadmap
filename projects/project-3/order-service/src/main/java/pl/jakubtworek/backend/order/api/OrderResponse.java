package pl.jakubtworek.backend.order.api;

import pl.jakubtworek.backend.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID reservationId,
        String userId,
        BigDecimal amount,
        OrderStatus status,
        Instant createdAt,
        String degradationReason
) {
}
