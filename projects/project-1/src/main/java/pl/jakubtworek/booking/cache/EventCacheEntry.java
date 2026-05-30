package pl.jakubtworek.booking.cache;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventCacheEntry(
        UUID eventId,
        String name,
        String city,
        String category,
        OffsetDateTime startsAt,
        int totalCapacity,
        int availableCapacity,
        Instant cachedAt,
        Instant expiresAt
) {
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
