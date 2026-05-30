package pl.jakubtworek.booking.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilitySnapshotCache {
    Optional<AvailabilitySnapshot> get(UUID eventId);
    void put(AvailabilitySnapshot snapshot, Duration ttl);
    void evict(UUID eventId);
}
