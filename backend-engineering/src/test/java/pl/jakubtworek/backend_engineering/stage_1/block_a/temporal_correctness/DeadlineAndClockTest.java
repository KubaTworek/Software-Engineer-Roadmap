package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeadlineAndClockTest {

    @Test
    void injectedClockMakesExpiryAndSafetyMarginDeterministic() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        Deadline deadline = Deadline.after(Duration.ofSeconds(10), clock);

        assertThat(deadline.remaining(clock)).isEqualTo(Duration.ofSeconds(10));
        assertThat(deadline.isUsable(clock, Duration.ofSeconds(3))).isTrue();

        clock.advance(Duration.ofSeconds(8));

        assertThat(deadline.isExpired(clock)).isFalse();
        assertThat(deadline.isUsable(clock, Duration.ofSeconds(3))).isFalse();

        clock.advance(Duration.ofSeconds(2));
        assertThat(deadline.isExpired(clock)).isTrue();
        assertThat(deadline.remaining(clock)).isZero();
    }

    @Test
    void skewCreatesAnUncertaintyWindowAroundTheBoundary() {
        Instant boundary = Instant.parse("2026-01-01T10:00:00Z");
        Duration maximumSkew = Duration.ofSeconds(2);

        assertThat(ClockSkewWindow.classify(boundary.minusSeconds(3), boundary, maximumSkew))
                .isEqualTo(ClockSkewWindow.Position.DEFINITELY_BEFORE);
        assertThat(ClockSkewWindow.classify(boundary.minusSeconds(1), boundary, maximumSkew))
                .isEqualTo(ClockSkewWindow.Position.UNCERTAIN);
        assertThat(ClockSkewWindow.classify(boundary.plusSeconds(2), boundary, maximumSkew))
                .isEqualTo(ClockSkewWindow.Position.DEFINITELY_AT_OR_AFTER);
    }

    @Test
    void elapsedDurationUsesAMonotonicTickerInsteadOfWallClock() {
        MutableClock wallClock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        AtomicLong ticker = new AtomicLong(1_000_000_000L);
        MonotonicTimer timer = MonotonicTimer.start(ticker::get);

        wallClock.moveBack(Duration.ofHours(1));
        ticker.addAndGet(Duration.ofMillis(250).toNanos());

        assertThat(wallClock.instant()).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
        assertThat(timer.elapsed()).isEqualTo(Duration.ofMillis(250));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void moveBack(Duration duration) {
            instant = instant.minus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
