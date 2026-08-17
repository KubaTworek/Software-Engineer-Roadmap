package com.example.newsfeed.feed;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeedSessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "feed:session:";

    private final StringRedisTemplate redisTemplate;

    public FeedSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public UUID createSession(List<FeedSessionEntry> entries) {
        UUID sessionId = UUID.randomUUID();
        String key = key(sessionId);
        if (!entries.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, entries.stream().map(FeedSessionEntry::encode).toList());
            redisTemplate.expire(key, SESSION_TTL);
        } else {
            redisTemplate.opsForValue().set(key + ":empty", "true", SESSION_TTL);
        }
        return sessionId;
    }

    public Optional<List<FeedSessionEntry>> getPage(UUID sessionId, int offset, int limit) {
        String key = key(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            Boolean emptyExists = redisTemplate.hasKey(key + ":empty");
            return Boolean.TRUE.equals(emptyExists) ? Optional.of(List.of()) : Optional.empty();
        }

        List<String> rawEntries = redisTemplate.opsForList().range(key, offset, offset + limit - 1L);
        redisTemplate.expire(key, SESSION_TTL);
        if (rawEntries == null) {
            return Optional.empty();
        }

        return Optional.of(rawEntries.stream().map(FeedSessionEntry::decode).toList());
    }

    public long size(UUID sessionId) {
        Long size = redisTemplate.opsForList().size(key(sessionId));
        return size == null ? 0 : size;
    }

    private String key(UUID sessionId) {
        return PREFIX + sessionId;
    }
}
