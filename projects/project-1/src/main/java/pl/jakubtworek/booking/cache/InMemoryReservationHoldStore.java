package pl.jakubtworek.booking.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!nosql-real")
public class InMemoryReservationHoldStore implements ReservationHoldStore {
    private final ConcurrentHashMap<UUID, ReservationHold> holds = new ConcurrentHashMap<>();

    @Override
    public ReservationHold create(UUID eventId, String customerEmail, Duration ttl) {
        Instant now = Instant.now();
        ReservationHold hold = new ReservationHold(UUID.randomUUID(), eventId, customerEmail, now, now.plus(ttl));
        holds.put(hold.holdId(), hold);
        return hold;
    }

    @Override
    public Optional<ReservationHold> find(UUID holdId) {
        ReservationHold hold = holds.get(holdId);
        if (hold == null) {
            return Optional.empty();
        }
        if (!hold.activeAt(Instant.now())) {
            holds.remove(holdId);
            return Optional.empty();
        }
        return Optional.of(hold);
    }

    @Override
    public int removeExpired() {
        Instant now = Instant.now();
        int before = holds.size();
        holds.entrySet().removeIf(entry -> !entry.getValue().activeAt(now));
        return before - holds.size();
    }
}
