package com.example.urlshortener.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.service.RateLimitService;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisCounterInfrastructureTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Test
    void actualRateLimiterAtomicallyCreatesCounterWithExpiry() throws Exception {
        var configuration = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        var connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        try {
            var redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            String key = "rl:redirect:educational-test";
            var limiter = new RateLimitService(redis);

            int workers = 8;
            int incrementsPerWorker = 100;
            List<Callable<Void>> increments = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                increments.add(() -> {
                    for (int attempt = 0; attempt < incrementsPerWorker; attempt++) {
                        // Redis INCR jest atomowy: równoległe procesy nie gubią aktualizacji licznika.
                        try {
                            limiter.checkFixedWindow(key, workers * incrementsPerWorker, Duration.ofMinutes(5));
                        } catch (RateLimitExceededException unexpected) {
                            throw new AssertionError(unexpected);
                        }
                    }
                    return null;
                });
            }

            try (var executor = Executors.newFixedThreadPool(workers)) {
                for (var future : executor.invokeAll(increments)) {
                    future.get();
                }
            }

            assertThat(redis.opsForValue().get(key)).isEqualTo("800");
            assertThat(redis.getExpire(key)).isPositive();
        } finally {
            connectionFactory.destroy();
        }
    }
}
