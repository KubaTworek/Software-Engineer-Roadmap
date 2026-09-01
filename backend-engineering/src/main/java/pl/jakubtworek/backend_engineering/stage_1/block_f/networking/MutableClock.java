package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Test clock used by time-sensitive networking models without sleeps. */
public final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneId.of("UTC"));
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        if (duration.isNegative()) throw new IllegalArgumentException("duration cannot be negative");
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        return new MutableClock(instant, requestedZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
