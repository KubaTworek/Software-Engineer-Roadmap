package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;
import java.time.Instant;

/** Distinguishes service time from user-visible latency corrected for delayed starts. */
public record LatencyMeasurement(Duration observed, Duration scheduleCorrected) {

    public LatencyMeasurement {
        if (observed == null || observed.isNegative()) throw new IllegalArgumentException("observed must be non-negative");
        if (scheduleCorrected == null || scheduleCorrected.compareTo(observed) < 0) {
            throw new IllegalArgumentException("scheduleCorrected must not be lower than observed");
        }
    }

    public static LatencyMeasurement from(Instant scheduledAt, Instant startedAt, Instant completedAt) {
        if (scheduledAt == null || startedAt == null || completedAt == null
                || startedAt.isBefore(scheduledAt) || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("timestamps must be ordered: scheduled <= started <= completed");
        }
        return new LatencyMeasurement(
                Duration.between(startedAt, completedAt),
                Duration.between(scheduledAt, completedAt));
    }
}
