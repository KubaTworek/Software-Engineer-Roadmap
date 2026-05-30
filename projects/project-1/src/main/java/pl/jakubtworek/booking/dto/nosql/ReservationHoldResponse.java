package pl.jakubtworek.booking.dto.nosql;

import java.time.Instant;
import java.util.UUID;

public record ReservationHoldResponse(
        UUID holdId,
        UUID eventId,
        String customerEmail,
        Instant createdAt,
        Instant expiresAt,
        boolean active
) {
}
