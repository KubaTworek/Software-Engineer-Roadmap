package pl.jakubtworek.booking.infrastructure;

import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Celowane testy kontraktu potwierdzają semantykę, której nie gwarantuje fake:
 * wykonanie skryptu Lua jako jednej operacji Redis oraz warunkową aktualizację
 * dokumentu przez prawdziwy silnik MongoDB.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisAndMongoContractsIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> MONGO = new GenericContainer<>(DockerImageName.parse("mongo:7"))
            .withExposedPorts(27017);

    @Test
    void redisIncrementAndFirstTtlAreOneAtomicOperation() throws Exception {
        var connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        var redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        var incrementWithTtl = new DefaultRedisScript<Long>("""
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                  redis.call('PEXPIRE', KEYS[1], ARGV[1])
                end
                return current
                """, Long.class);
        String key = "quota:" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(8);

        try {
            var calls = java.util.stream.IntStream.range(0, 40)
                    .<java.util.concurrent.Callable<Long>>mapToObj(ignored ->
                            () -> redis.execute(incrementWithTtl, List.of(key), "30000"))
                    .toList();
            var results = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    })
                    .sorted()
                    .toList();

            assertThat(results).containsExactlyElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, 40).boxed().toList());
            assertThat(redis.getExpire(key)).isPositive();
            assertThat(redis.opsForValue().get(key)).isEqualTo("40");
        } finally {
            executor.shutdownNow();
            connectionFactory.destroy();
        }
    }

    @Test
    void mongoProjectionRejectsAnOlderOutOfOrderEvent() {
        String connectionString = "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017);
        try (var client = MongoClients.create(connectionString)) {
            var projections = client.getDatabase("booking_lab").getCollection("event_search");
            String eventId = UUID.randomUUID().toString();
            projections.insertOne(new Document("_id", eventId)
                    .append("sourceVersion", 10L)
                    .append("name", "Current name"));

            /*
             * Konsument nie polega na kolejności dostarczenia. Wersja źródłowa
             * jest częścią warunku zapisu, więc spóźnione zdarzenie v9 nie może
             * cofnąć projekcji zbudowanej już ze zdarzenia v10.
             */
            var stale = projections.updateOne(
                    and(eq("_id", eventId), lt("sourceVersion", 9L)),
                    combine(set("sourceVersion", 9L), set("name", "Stale name")));
            var fresh = projections.updateOne(
                    and(eq("_id", eventId), lt("sourceVersion", 11L)),
                    combine(set("sourceVersion", 11L), set("name", "Fresh name")));

            assertThat(stale.getMatchedCount()).isZero();
            assertThat(fresh.getModifiedCount()).isEqualTo(1);
            Document stored = projections.find(eq("_id", eventId)).first();
            assertThat(stored).isNotNull();
            assertThat(stored.getString("name")).isEqualTo("Fresh name");
        }
    }
}
