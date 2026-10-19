package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Classifies a boundary without pretending that two machine clocks are identical. */
public final class ClockSkewWindow {

    private ClockSkewWindow() {
    }

    public static Position classify(Instant localNow, Instant boundary, Duration maximumSkew) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        Objects.requireNonNull(boundary, "boundary must not be null");
        Objects.requireNonNull(maximumSkew, "maximumSkew must not be null");
        if (maximumSkew.isNegative()) {
            throw new IllegalArgumentException("maximumSkew must not be negative");
        }

        if (localNow.plus(maximumSkew).isBefore(boundary)) {
            return Position.DEFINITELY_BEFORE;
        }
        if (!localNow.minus(maximumSkew).isBefore(boundary)) {
            return Position.DEFINITELY_AT_OR_AFTER;
        }
        return Position.UNCERTAIN;
    }

    public enum Position {
        DEFINITELY_BEFORE,
        UNCERTAIN,
        DEFINITELY_AT_OR_AFTER
    }
}
