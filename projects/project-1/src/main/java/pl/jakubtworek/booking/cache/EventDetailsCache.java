package pl.jakubtworek.booking.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface EventDetailsCache {
    Optional<EventCacheEntry> get(UUID eventId);
    void put(EventCacheEntry entry, Duration ttl);
    void evict(UUID eventId);
}
