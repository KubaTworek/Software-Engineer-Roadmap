package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.InMemoryTtlCache;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.queue.IdempotentQueueWorker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.queue.QueueMessage;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit.RateLimitDecision;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit.TokenBucketRateLimiter;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RetryExecutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemDesignResilienceTest {

    @Test
    void cacheExpiresAtTheTtlBoundaryWithoutSleeping() {
        AtomicLong time = new AtomicLong(100);
        InMemoryTtlCache<String, String> cache = new InMemoryTtlCache<>(time::get);

        cache.put("product:1", "book", Duration.ofNanos(10));

        assertThat(cache.get("product:1")).contains("book");
        time.set(110);
        assertThat(cache.get("product:1")).isEmpty();
    }

    @Test
    void cacheRejectsInvalidEntries() {
        InMemoryTtlCache<String, String> cache = new InMemoryTtlCache<>();

        assertThatThrownBy(() -> cache.put("key", "value", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.put("key", null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tokenBucketRefillsAtConfiguredRate() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1, clock);

        assertThat(limiter.allow("customer-1").allowed()).isTrue();
        assertThat(limiter.allow("customer-1").allowed()).isTrue();
        RateLimitDecision rejected = limiter.allow("customer-1");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.allow("customer-1").allowed()).isTrue();
    }

    @Test
    void failedQueueHandlingCanBeRetriedButSuccessfulDuplicateIsIgnored() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        IdempotentQueueWorker<String> worker = new IdempotentQueueWorker<>(message -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
        });
        QueueMessage<String> message = new QueueMessage<>("message-1", "payload", Instant.now());

        assertThatThrownBy(() -> worker.process(message)).isInstanceOf(IllegalStateException.class);
        worker.process(message);
        worker.process(message);

        assertThat(calls).hasValue(2);
    }

    @Test
    void queueAgeUsesTheInjectedClockAndNeverBecomesNegative() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:10Z"), ZoneOffset.UTC);
        IdempotentQueueWorker<String> worker = new IdempotentQueueWorker<>(ignored -> { }, clock);

        assertThat(worker.messageAge(new QueueMessage<>(
                "message-1",
                "payload",
                Instant.parse("2026-01-01T00:00:04Z")
        ))).isEqualTo(Duration.ofSeconds(6));
        assertThat(worker.messageAge(new QueueMessage<>(
                "message-2",
                "payload",
                Instant.parse("2026-01-01T00:00:11Z")
        ))).isZero();
    }

    @Test
    void retryStopsAtAttemptLimitAndSkipsPermanentFailures() {
        AtomicInteger transientCalls = new AtomicInteger();
        RetryExecutor executor = new RetryExecutor(
                3,
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                exception -> exception instanceof IllegalStateException
        );

        assertThatThrownBy(() -> executor.execute(() -> {
            transientCalls.incrementAndGet();
            throw new IllegalStateException("still unavailable");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(transientCalls).hasValue(3);

        AtomicInteger permanentCalls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(() -> {
            permanentCalls.incrementAndGet();
            throw new IllegalArgumentException("invalid request");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(permanentCalls).hasValue(1);
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
