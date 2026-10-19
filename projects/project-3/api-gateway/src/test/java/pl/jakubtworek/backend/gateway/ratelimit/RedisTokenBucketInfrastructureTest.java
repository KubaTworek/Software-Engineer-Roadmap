package pl.jakubtworek.backend.gateway.ratelimit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Sprawdza atomowy Lua i TTL na prawdziwym Redisie, nie na lokalnym fake'u. */
@Tag("infrastructure")
@Testcontainers
class RedisTokenBucketInfrastructureTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void sharedBucketNeverAllowsMoreThanCapacityAndExpires() {
        var connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        try {
            var template = new StringRedisTemplate(connection);
            template.afterPropertiesSet();
            var limiterA = new RedisTokenBucketRateLimiter(template);
            var limiterB = new RedisTokenBucketRateLimiter(template);
            var bucket = new RateLimitProperties.Bucket(3, 3, Duration.ofMinutes(1));

            assertThat(limiterA.consume("shared", bucket).allowed()).isTrue();
            assertThat(limiterB.consume("shared", bucket).allowed()).isTrue();
            assertThat(limiterA.consume("shared", bucket).allowed()).isTrue();
            assertThat(limiterB.consume("shared", bucket).allowed()).isFalse();
            assertThat(template.getExpire("shared")).isPositive();
        } finally {
            connection.destroy();
        }
    }
}
