package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Deadline(Instant expiresAt) {

    public Deadline {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static Deadline after(Duration budget, Clock clock) {
        Objects.requireNonNull(budget, "budget must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive");
        }
        return new Deadline(clock.instant().plus(budget));
    }

    public Duration remaining(Clock clock) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isExpired(Clock clock) {
        return !clock.instant().isBefore(expiresAt);
    }

    /** Fail closed before actual expiry to absorb a known clock-skew and transport margin. */
    public boolean isUsable(Clock clock, Duration safetyMargin) {
        Objects.requireNonNull(safetyMargin, "safetyMargin must not be null");
        if (safetyMargin.isNegative()) {
            throw new IllegalArgumentException("safetyMargin must not be negative");
        }
        return clock.instant().plus(safetyMargin).isBefore(expiresAt);
    }
}
