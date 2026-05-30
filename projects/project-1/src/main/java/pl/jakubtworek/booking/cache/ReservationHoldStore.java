package pl.jakubtworek.booking.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ReservationHoldStore {
    ReservationHold create(UUID eventId, String customerEmail, Duration ttl);
    Optional<ReservationHold> find(UUID holdId);
    int removeExpired();
}
