package pl.jakubtworek.booking.cache;

import java.time.Instant;
import java.util.UUID;

public record AvailabilitySnapshot(
        UUID eventId,
        int totalCapacity,
        int availableCapacity,
        Instant snapshotAt,
        Instant expiresAt
) {
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
