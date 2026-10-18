package com.ridesharing.location;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Weryfikuje Lua/Redis, a nie pamięciową atrapę geospatial storage. */
@Testcontainers(disabledWithoutDocker = true)
class RedisLocationInfrastructureTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @Test
    void newerSequenceWinsAndDriverLeavesPreviousH3Cell() throws Exception {
        var indexer = new H3GeoCellIndexer(8);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var service = new LocationService(redis, indexer, new LocationEventPublisher(kafka, "locations"), 30);

        var first = request(1, 52.2297, 21.0122);
        service.update(first);
        String oldCell = indexer.cell(first.lat(), first.lng());

        var newest = request(2, 52.3000, 21.1000);
        service.update(newest);
        var staleResult = service.update(request(1, 50.0000, 19.0000));

        assertThat(staleResult.sequence()).isEqualTo(2);
        assertThat(staleResult.lat()).isEqualTo(newest.lat());
        assertThat(redis.opsForSet().isMember(LocationKeys.cell("warsaw", oldCell), "driver-1")).isFalse();
    }

    private UpdateDriverLocationRequest request(long sequence, double lat, double lng) {
        return new UpdateDriverLocationRequest(
                "driver-1", "warsaw", lat, lng, 0.0, 0.0, 5.0,
                sequence, Instant.parse("2026-01-01T10:00:00Z").plusSeconds(sequence)
        );
    }
}
