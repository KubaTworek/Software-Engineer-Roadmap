package pl.jakubtworek.booking.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("nosql-real")
public class RedisReservationHoldStore implements ReservationHoldStore {
    private static final String PREFIX = "reservation-hold:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisReservationHoldStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReservationHold create(UUID eventId, String customerEmail, Duration ttl) {
        Instant now = Instant.now();
        ReservationHold hold = new ReservationHold(UUID.randomUUID(), eventId, customerEmail, now, now.plus(ttl));
        try {
            redis.opsForValue().set(PREFIX + hold.holdId(), objectMapper.writeValueAsString(hold), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize reservation hold", ex);
        }
        return hold;
    }

    @Override
    public Optional<ReservationHold> find(UUID holdId) {
        String json = redis.opsForValue().get(PREFIX + holdId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ReservationHold.class));
        } catch (JsonProcessingException ex) {
            redis.delete(PREFIX + holdId);
            return Optional.empty();
        }
    }

    @Override
    public int removeExpired() {
        return 0;
    }
}
