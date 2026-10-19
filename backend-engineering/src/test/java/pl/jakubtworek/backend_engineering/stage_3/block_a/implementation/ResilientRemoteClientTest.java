package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreakerOpenException;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreakerState;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RetryExecutor;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutConfig;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.remote.ResilientRemoteClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientRemoteClientTest {

    @Test
    void timeoutWrapsEveryRetryBeforeBreakerRecordsLogicalFailureAndFallbackRunsOnce() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger dependencyCalls = new AtomicInteger();
        CircuitBreaker breaker = new CircuitBreaker("catalog", 1, Duration.ofMinutes(1));
        RetryExecutor retry = retryingEveryIllegalStateThreeTimes();

        try (TimeoutExecutor timeout = new RecordingTimeoutExecutor(events)) {
            ResilientRemoteClient client = new ResilientRemoteClient(
                    breaker,
                    retry,
                    timeout,
                    new TimeoutConfig(Duration.ofMillis(50), Duration.ofMillis(100))
            );

            String result = client.execute(
                    () -> {
                        int attempt = dependencyCalls.incrementAndGet();
                        events.add("dependency-attempt-" + attempt);
                        throw new IllegalStateException("catalog unavailable");
                    },
                    failure -> {
                        events.add("fallback-after-" + breaker.state());
                        assertThat(failure).isInstanceOf(IllegalStateException.class);
                        return "catalog-without-recommendations";
                    }
            );

            assertThat(result).isEqualTo("catalog-without-recommendations");
            assertThat(events).containsExactly(
                    "timeout-boundary-1",
                    "dependency-attempt-1",
                    "timeout-boundary-2",
                    "dependency-attempt-2",
                    "timeout-boundary-3",
                    "dependency-attempt-3",
                    "fallback-after-OPEN"
            );

            String failFastResult = client.execute(
                    () -> {
                        dependencyCalls.incrementAndGet();
                        return "must-not-run";
                    },
                    failure -> failure.getClass().getSimpleName()
            );

            assertThat(failFastResult).isEqualTo("CircuitBreakerOpenException");
            assertThat(dependencyCalls).hasValue(3);
        }
    }

    @Test
    void retryOutsideCircuitBreakerWastesAttemptsOnAlreadyOpenCircuit() {
        AtomicInteger retryLayerCalls = new AtomicInteger();
        AtomicInteger dependencyCalls = new AtomicInteger();
        CircuitBreaker breaker = new CircuitBreaker("payments", 1, Duration.ofMinutes(1));
        RetryExecutor dangerouslyBroadRetry = new RetryExecutor(
                3,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                ignored -> true
        );

        assertThatThrownBy(() -> dangerouslyBroadRetry.execute(() -> {
            retryLayerCalls.incrementAndGet();
            return breaker.execute(() -> {
                dependencyCalls.incrementAndGet();
                throw new IllegalStateException("payments unavailable");
            });
        })).isInstanceOf(CircuitBreakerOpenException.class);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(retryLayerCalls).hasValue(3);
        assertThat(dependencyCalls).hasValue(1);
    }

    private static RetryExecutor retryingEveryIllegalStateThreeTimes() {
        return new RetryExecutor(
                3,
                Duration.ofMillis(1),
                Duration.ofMillis(1),
                failure -> failure instanceof IllegalStateException
        );
    }

    private static final class RecordingTimeoutExecutor extends TimeoutExecutor {

        private final List<String> events;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingTimeoutExecutor(List<String> events) {
            super(Executors.newSingleThreadExecutor());
            this.events = events;
        }

        @Override
        public <T> T execute(Callable<T> operation, Duration timeout) throws Exception {
            events.add("timeout-boundary-" + calls.incrementAndGet());
            return super.execute(operation, timeout);
        }
    }
}
