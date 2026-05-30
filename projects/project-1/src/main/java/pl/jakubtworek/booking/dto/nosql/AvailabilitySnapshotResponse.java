package pl.jakubtworek.booking.dto.nosql;

import java.time.Instant;
import java.util.UUID;

public record AvailabilitySnapshotResponse(
        UUID eventId,
        int totalCapacity,
        int availableCapacity,
        String source,
        Instant snapshotAt,
        Instant expiresAt
) {
}
