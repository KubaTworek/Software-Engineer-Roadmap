package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.CacheAsideService;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.InMemoryTtlCache;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.TtlJitter;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreakerOpenException;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreakerState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheAsideAndCircuitBreakerTest {

    @Test
    void cacheAsideCoalescesConcurrentMissesAndReloadsAfterExpiry() throws Exception {
        AtomicLong nanoTime = new AtomicLong();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        InMemoryTtlCache<String, String> cache = new InMemoryTtlCache<>(nanoTime::get);
        CacheAsideService<String, String> service = new CacheAsideService<>(
                cache,
                key -> {
                    loads.incrementAndGet();
                    loaderEntered.countDown();
                    await(releaseLoader);
                    return "value-" + key;
                },
                Duration.ofNanos(10),
                new TtlJitter(Duration.ZERO)
        );

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> service.get("product-1")))
                    .toList();

            assertThat(loaderEntered.await(1, TimeUnit.SECONDS)).isTrue();
            releaseLoader.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(1, TimeUnit.SECONDS)).isEqualTo("value-product-1");
            }
            assertThat(loads).hasValue(1);

            nanoTime.set(10);
            assertThat(service.get("product-1")).isEqualTo("value-product-1");
            assertThat(loads).hasValue(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void halfOpenAllowsExactlyOneTrialAndClosesAtTimeoutBoundary() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        CircuitBreaker breaker = new CircuitBreaker("payments", 1, Duration.ofSeconds(30), clock);
        assertThatThrownBy(() -> breaker.execute(() -> { throw new IllegalStateException("down"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

        clock.advance(Duration.ofSeconds(30));
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);

        CountDownLatch trialEntered = new CountDownLatch(1);
        CountDownLatch releaseTrial = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> trial = executor.submit(() -> breaker.execute(() -> {
                trialEntered.countDown();
                releaseTrial.await();
                return "recovered";
            }));

            assertThat(trialEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> breaker.execute(() -> "second trial"))
                    .isInstanceOf(CircuitBreakerOpenException.class);

            releaseTrial.countDown();
            assertThat(trial.get(1, TimeUnit.SECONDS)).isEqualTo("recovered");
            assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
        } finally {
            releaseTrial.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void callerErrorsDoNotOpenTheDependencyCircuit() {
        CircuitBreaker breaker = new CircuitBreaker(
                "payments",
                1,
                Duration.ofSeconds(30),
                Clock.systemUTC(),
                exception -> !(exception instanceof IllegalArgumentException)
        );

        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new IllegalArgumentException("invalid amount");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
