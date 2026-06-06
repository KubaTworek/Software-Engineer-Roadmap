package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkFixedWindow(String key, long limit, Duration window) {
        if (limit <= 0) {
            return;
        }

        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, window);
            }

            if (current != null && current > limit) {
                Long ttl = redisTemplate.getExpire(key);
                long retryAfter = ttl == null || ttl < 0 ? window.toSeconds() : ttl;
                throw new RateLimitExceededException("Too many requests. Try again later.", retryAfter);
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Redis rate-limit check failed for key={}. Failing open.", key, exception);
        }
    }
}
