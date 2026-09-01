package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicFixedWindowRateLimiterTest {

    @Test
    void shouldRejectRequestsAfterTheLimitWithoutExtendingTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T10:00:00Z"));
        AtomicFixedWindowRateLimiter limiter = new AtomicFixedWindowRateLimiter(clock);

        var first = limiter.tryAcquire("user:1", 2, Duration.ofMinutes(1));
        var second = limiter.tryAcquire("user:1", 2, Duration.ofMinutes(1));
        var rejected = limiter.tryAcquire("user:1", 2, Duration.ofMinutes(1));

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.resetAt()).isEqualTo(first.resetAt());
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldResetExactlyAtTheTtlBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T10:00:00Z"));
        AtomicFixedWindowRateLimiter limiter = new AtomicFixedWindowRateLimiter(clock);
        limiter.tryAcquire("user:1", 1, Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(30));

        assertThat(limiter.tryAcquire("user:1", 1, Duration.ofSeconds(30)).allowed()).isTrue();
    }

    @Test
    void shouldApplyOneAtomicLimitUnderConcurrentCalls() throws Exception {
        AtomicFixedWindowRateLimiter limiter = new AtomicFixedWindowRateLimiter(
                Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC)
        );
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                calls.add(() -> limiter.tryAcquire("shared-key", 10, Duration.ofMinutes(1)).allowed());
            }

            long allowed = executor.invokeAll(calls).stream()
                    .filter(future -> get(future))
                    .count();

            assertThat(allowed).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean get(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("test clock uses UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
