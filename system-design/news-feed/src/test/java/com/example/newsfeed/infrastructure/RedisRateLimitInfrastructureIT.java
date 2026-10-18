package com.example.newsfeed.infrastructure;

import com.example.newsfeed.ratelimit.RateLimitExceededException;
import com.example.newsfeed.ratelimit.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprawdza atomowy kontrakt licznika i TTL na prawdziwym Redisie. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitInfrastructureIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void firstIncrementSetsTtlAndLimitRejectsFollowingRequest() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            RateLimitService limiter = new RateLimitService(template);

            limiter.check("feed", "user-42", 2);
            limiter.check("feed", "user-42", 2);
            assertThatThrownBy(() -> limiter.check("feed", "user-42", 2))
                    .isInstanceOf(RateLimitExceededException.class);

            byte[] key = template.getConnectionFactory().getConnection()
                    .scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match("rate:feed:user-42:*").count(10).build())
                    .next();
            assertThat(key).isNotNull();
            assertThat(template.getExpire(new String(key, StandardCharsets.UTF_8))).isPositive();
        } finally {
            factory.destroy();
        }
    }
}
