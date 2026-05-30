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
public class RedisEventDetailsCache implements EventDetailsCache {
    private static final String PREFIX = "event-details:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisEventDetailsCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<EventCacheEntry> get(UUID eventId) {
        String json = redis.opsForValue().get(PREFIX + eventId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, EventCacheEntry.class));
        } catch (JsonProcessingException ex) {
            evict(eventId);
            return Optional.empty();
        }
    }

    @Override
    public void put(EventCacheEntry entry, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + entry.eventId(), objectMapper.writeValueAsString(entry), ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize event cache entry", ex);
        }
    }

    @Override
    public void evict(UUID eventId) {
        redis.delete(PREFIX + eventId);
    }
}
