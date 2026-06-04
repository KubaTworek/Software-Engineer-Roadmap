package pl.jakubtworek.backend.catalog.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class RedisJsonCache {
    private static final Logger log = LoggerFactory.getLogger(RedisJsonCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public RedisJsonCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public <T> T getOrLoad(String cacheName, String key, Duration ttl, JavaType javaType, Supplier<T> loader) {
        String redisKey = "cache:" + cacheName + ":" + key;
        T cached = read(redisKey, cacheName, javaType);
        if (cached != null) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(redisKey, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = read(redisKey, cacheName, javaType);
                if (cached != null) {
                    return cached;
                }
                T value = loader.get();
                write(redisKey, cacheName, ttl, value);
                return value;
            } finally {
                locks.remove(redisKey);
            }
        }
    }

    private <T> T read(String redisKey, String cacheName, JavaType javaType) {
        try {
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json == null) {
                counter("miss", cacheName).increment();
                return null;
            }
            counter("hit", cacheName).increment();
            return objectMapper.readValue(json, javaType);
        } catch (DataAccessException exception) {
            counter("unavailable", cacheName).increment();
            log.warn("Redis cache unavailable. Falling back to source. key={}", redisKey, exception);
            return null;
        } catch (Exception exception) {
            counter("read_error", cacheName).increment();
            log.warn("Redis cache read failed. Evicting bad value. key={}", redisKey, exception);
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception ignored) {
                // best effort
            }
            return null;
        }
    }

    private void write(String redisKey, String cacheName, Duration ttl, Object value) {
        Objects.requireNonNull(value, "value");
        try {
            Duration ttlWithJitter = ttl.plusSeconds(ThreadLocalRandom.current().nextLong(1, 11));
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(value), ttlWithJitter);
            counter("write", cacheName).increment();
        } catch (DataAccessException exception) {
            counter("unavailable", cacheName).increment();
            log.warn("Redis cache unavailable. Returning uncached value. key={}", redisKey, exception);
        } catch (Exception exception) {
            counter("write_error", cacheName).increment();
            log.warn("Redis cache write failed. key={}", redisKey, exception);
        }
    }

    private Counter counter(String result, String cacheName) {
        return Counter.builder("app_cache_requests_total")
                .tag("cache", cacheName)
                .tag("result", result)
                .register(meterRegistry);
    }
}
