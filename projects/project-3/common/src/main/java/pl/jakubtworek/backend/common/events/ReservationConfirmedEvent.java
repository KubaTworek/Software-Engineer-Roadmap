package pl.jakubtworek.backend.common.events;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmedEvent(
        UUID eventId,
        UUID reservationId,
        String userId,
        int quantity,
        Instant occurredAt
) implements Serializable {
    public static ReservationConfirmedEvent now(UUID eventId, UUID reservationId, String userId, int quantity) {
        return new ReservationConfirmedEvent(eventId, reservationId, userId, quantity, Instant.now());
    }
}
