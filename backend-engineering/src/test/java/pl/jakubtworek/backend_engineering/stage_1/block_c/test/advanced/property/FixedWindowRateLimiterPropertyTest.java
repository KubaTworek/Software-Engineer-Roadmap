package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.property;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value.AtomicFixedWindowRateLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;

class FixedWindowRateLimiterPropertyTest {

    @Test
    void aWindowShouldNeverAllowMoreThanItsGeneratedLimit() {
        qt().withExamples(400)
                .forAll(integers().between(1, 100), integers().between(0, 250))
                .checkAssert(this::assertWindowInvariant);
    }

    private void assertWindowInvariant(int limit, int attempts) {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T12:00:00Z"));
        AtomicFixedWindowRateLimiter limiter = new AtomicFixedWindowRateLimiter(clock);
        int allowed = 0;

        for (int attempt = 0; attempt < attempts; attempt++) {
            AtomicFixedWindowRateLimiter.RateLimitDecision decision =
                    limiter.tryAcquire("tenant-7", limit, Duration.ofMinutes(1));
            if (decision.allowed()) {
                allowed++;
            }
            assertThat(decision.remaining()).isBetween(0, limit);
            assertThat(decision.retryAfter().isNegative()).isFalse();
        }

        assertThat(allowed).isEqualTo(Math.min(limit, attempts));

        clock.advance(Duration.ofMinutes(1));
        AtomicFixedWindowRateLimiter.RateLimitDecision firstInNextWindow =
                limiter.tryAcquire("tenant-7", limit, Duration.ofMinutes(1));
        assertThat(firstInNextWindow.allowed()).isTrue();
        assertThat(firstInNextWindow.remaining()).isEqualTo(limit - 1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("laboratory clock uses UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
