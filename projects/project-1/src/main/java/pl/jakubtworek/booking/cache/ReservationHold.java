package pl.jakubtworek.booking.cache;

import java.time.Instant;
import java.util.UUID;

public record ReservationHold(
        UUID holdId,
        UUID eventId,
        String customerEmail,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean activeAt(Instant now) {
        return expiresAt.isAfter(now);
    }
}
