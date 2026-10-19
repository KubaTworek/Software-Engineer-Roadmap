package com.example.videostreaming.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisAtomicCounterInfrastructureTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void incrementAndFirstTtlAreOneAtomicRedisOperation() {
        LettuceConnectionFactory connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            redis.afterPropertiesSet();
            DefaultRedisScript<Long> script = new DefaultRedisScript<>("""
                    local value = redis.call('INCR', KEYS[1])
                    if value == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
                    return value
                    """, Long.class);

            assertThat(redis.execute(script, List.of("qoe:session"), "10000")).isEqualTo(1L);
            assertThat(redis.execute(script, List.of("qoe:session"), "10000")).isEqualTo(2L);
            assertThat(redis.getExpire("qoe:session")).isPositive();
        } finally {
            connection.destroy();
        }
    }
}
