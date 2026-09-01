package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlinePropagationTest {

    @Test
    void everyHopShouldShortenTheAbsoluteDeadlineAndKeepAParentReserve() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T10:00:00Z"));
        RequestDeadline incoming = RequestDeadline.after(Duration.ofSeconds(1), clock);

        RequestDeadline firstHop = incoming.child(Duration.ofMillis(400), Duration.ofMillis(200));
        assertThat(firstHop.expiresAt()).isEqualTo(clock.instant().plusMillis(400));

        clock.advance(Duration.ofMillis(350));
        RequestDeadline propagated = RequestDeadline.fromHeader(incoming.toHeaderValue(), clock);
        RequestDeadline secondHop = propagated.child(Duration.ofMillis(500), Duration.ofMillis(300));

        assertThat(secondHop.expiresAt()).isEqualTo(incoming.expiresAt().minusMillis(300));
        assertThat(secondHop.remaining()).isEqualTo(Duration.ofMillis(350));

        clock.advance(Duration.ofMillis(351));
        assertThatThrownBy(() -> propagated.child(Duration.ofMillis(100), Duration.ofMillis(300)))
                .isInstanceOf(DeadlineExceededException.class);
    }

    @Test
    void propagatedHeaderShouldRepresentAnInstantRatherThanStartingANewTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T10:00:00Z"));
        RequestDeadline original = RequestDeadline.after(Duration.ofMillis(800), clock);

        clock.advance(Duration.ofMillis(500));
        RequestDeadline receivedByDownstream = RequestDeadline.fromHeader(original.toHeaderValue(), clock);

        assertThat(receivedByDownstream.expiresAt()).isEqualTo(original.expiresAt());
        assertThat(receivedByDownstream.remaining()).isEqualTo(Duration.ofMillis(300));
        assertThat(RequestDeadline.HEADER).isEqualTo("X-Request-Deadline-Epoch-Millis");
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
