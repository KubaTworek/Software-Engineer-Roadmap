package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** A clock advanced only by the deterministic simulation. */
public final class ControlledClock extends Clock {

    private Instant current;

    public ControlledClock(Instant initialTime) {
        this.current = Objects.requireNonNull(initialTime, "initialTime must not be null");
    }

    public synchronized void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        current = current.plus(duration);
    }

    synchronized void advanceTo(Instant target) {
        if (target.isBefore(current)) {
            throw new IllegalArgumentException("controlled clock cannot move backwards");
        }
        current = target;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        Objects.requireNonNull(zone, "zone must not be null");
        return this;
    }

    @Override
    public synchronized Instant instant() {
        return current;
    }
}
