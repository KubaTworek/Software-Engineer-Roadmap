package pl.jakubtworek.booking.dto.nosql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventCacheResponse(
        UUID eventId,
        String name,
        String city,
        String category,
        OffsetDateTime startsAt,
        int totalCapacity,
        int availableCapacity,
        String source,
        Instant cachedAt,
        Instant expiresAt
) {
}
