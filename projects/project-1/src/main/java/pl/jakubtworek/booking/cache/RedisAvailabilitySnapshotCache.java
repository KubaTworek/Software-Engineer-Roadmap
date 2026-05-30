package pl.jakubtworek.booking.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("nosql-real")
public class RedisAvailabilitySnapshotCache implements AvailabilitySnapshotCache {
    private static final String PREFIX = "availability:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisAvailabilitySnapshotCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AvailabilitySnapshot> get(UUID eventId) {
        String json = redis.opsForValue().get(PREFIX + eventId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AvailabilitySnapshot.class));
        } catch (JsonProcessingException ex) {
            evict(eventId);
            return Optional.empty();
        }
    }

    @Override
    public void put(AvailabilitySnapshot snapshot, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + snapshot.eventId(), objectMapper.writeValueAsString(snapshot), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize availability snapshot", ex);
        }
    }

    @Override
    public void evict(UUID eventId) {
        redis.delete(PREFIX + eventId);
    }
}
