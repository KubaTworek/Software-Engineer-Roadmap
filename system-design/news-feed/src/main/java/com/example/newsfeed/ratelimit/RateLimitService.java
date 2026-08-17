package com.example.newsfeed.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(String bucket, String identity, int limitPerMinute) {
        String key = "rate:" + bucket + ":" + identity + ":" + (System.currentTimeMillis() / 60_000);
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(2));
        }

        if (current != null && current > limitPerMinute) {
            throw new RateLimitExceededException("Rate limit exceeded for " + bucket + ".");
        }
    }
}
