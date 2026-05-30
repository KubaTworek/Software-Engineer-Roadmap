package pl.jakubtworek.booking.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!nosql-real")
public class InMemoryEventDetailsCache implements EventDetailsCache {
    private final ConcurrentHashMap<UUID, EventCacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryEventDetailsCache() {
        this.clock = Clock.systemUTC();
    }

    @Override
    public Optional<EventCacheEntry> get(UUID eventId) {
        EventCacheEntry entry = entries.get(eventId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired(Instant.now(clock))) {
            entries.remove(eventId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public void put(EventCacheEntry entry, Duration ttl) {
        entries.put(entry.eventId(), entry);
    }

    @Override
    public void evict(UUID eventId) {
        entries.remove(eventId);
    }
}
