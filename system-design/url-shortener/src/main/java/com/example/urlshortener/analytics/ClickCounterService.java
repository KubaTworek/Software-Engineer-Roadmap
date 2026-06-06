package com.example.urlshortener.analytics;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClickCounterService {
    private static final Duration COUNTER_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    public ClickCounterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementTotal(String shortCode) {
        increment("analytics:clicks:total:" + shortCode);
    }

    public void incrementDaily(String shortCode, LocalDate date) {
        increment("analytics:clicks:daily:" + shortCode + ":" + date);
    }

    public Optional<Long> getTotal(String shortCode) {
        return getLong("analytics:clicks:total:" + shortCode);
    }

    public Optional<Long> getDaily(String shortCode, LocalDate date) {
        return getLong("analytics:clicks:daily:" + shortCode + ":" + date);
    }

    private void increment(String key) {
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, COUNTER_TTL);
        } catch (DataAccessException ignored) {
            // Redis is an optimization for counters, not a hard dependency for redirects.
        }
    }

    private Optional<Long> getLong(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) return Optional.empty();
            return Optional.of(Long.parseLong(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
