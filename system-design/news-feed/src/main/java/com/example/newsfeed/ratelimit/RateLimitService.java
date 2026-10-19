package com.example.newsfeed.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Clock;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Autowired
    public RateLimitService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    public void check(String bucket, String identity, int limitPerMinute) {
        String key = "rate:" + bucket + ":" + identity + ":" + (clock.millis() / 60_000);
        Long current = redisTemplate.execute(INCREMENT_WITH_TTL, java.util.List.of(key),
                String.valueOf(Duration.ofMinutes(2).toMillis()));

        if (current != null && current > limitPerMinute) {
            throw new RateLimitExceededException("Rate limit exceeded for " + bucket + ".");
        }
    }
}
