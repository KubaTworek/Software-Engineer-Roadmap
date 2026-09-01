package pl.jakubtworek.backend_engineering.stage_1.block_d.infrastructure;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value.RedisFixedWindowScripts;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("infrastructure")
@Testcontainers
class RedisAtomicCounterContainerTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(REDIS_PORT);

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    @BeforeEach
    void connect() {
        client = RedisClient.create(
                "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))
        );
        connection = client.connect();
        connection.sync().flushdb();
    }

    @AfterEach
    void disconnect() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void luaScriptSerializesConcurrentIncrementsAndAlwaysCreatesTtl() throws Exception {
        String key = "rate-limit:customer-42";
        Duration window = Duration.ofSeconds(5);
        int requests = 20;
        List<Callable<Long>> increments = new ArrayList<>();

        for (int request = 0; request < requests; request++) {
            increments.add(() -> connection.sync().eval(
                    RedisFixedWindowScripts.INCREMENT_AND_SET_TTL,
                    ScriptOutputType.INTEGER,
                    new String[]{key},
                    Long.toString(window.toMillis())
            ));
        }

        List<Long> observedValues = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var result : executor.invokeAll(increments)) {
                observedValues.add(result.get());
            }
        }
        Collections.sort(observedValues);

        assertThat(observedValues).containsExactlyElementsOf(expectedSequence(requests));
        assertThat(connection.sync().get(key)).isEqualTo(Integer.toString(requests));
        assertThat(connection.sync().pttl(key))
                .isPositive()
                .isLessThanOrEqualTo(window.toMillis());
    }

    private List<Long> expectedSequence(int size) {
        List<Long> sequence = new ArrayList<>();
        for (long value = 1; value <= size; value++) {
            sequence.add(value);
        }
        return sequence;
    }
}
