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
public class InMemoryAvailabilitySnapshotCache implements AvailabilitySnapshotCache {
    private final ConcurrentHashMap<UUID, AvailabilitySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @Override
    public Optional<AvailabilitySnapshot> get(UUID eventId) {
        AvailabilitySnapshot snapshot = snapshots.get(eventId);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (snapshot.expired(Instant.now(clock))) {
            snapshots.remove(eventId);
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    @Override
    public void put(AvailabilitySnapshot snapshot, Duration ttl) {
        snapshots.put(snapshot.eventId(), snapshot);
    }

    @Override
    public void evict(UUID eventId) {
        snapshots.remove(eventId);
    }
}
