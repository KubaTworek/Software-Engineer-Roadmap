package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import java.time.Duration;
import java.time.Instant;

public record RecurringJobDefinition(
        String name,
        Instant firstScheduledAt,
        Duration interval,
        MisfirePolicy misfirePolicy,
        int maximumCatchUpRuns
) {

    public RecurringJobDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (firstScheduledAt == null) {
            throw new IllegalArgumentException("firstScheduledAt is required");
        }
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        if (misfirePolicy == null) {
            throw new IllegalArgumentException("misfirePolicy is required");
        }
        if (maximumCatchUpRuns < 1) {
            throw new IllegalArgumentException("maximumCatchUpRuns must be positive");
        }
    }

    public enum MisfirePolicy {
        SKIP,
        FIRE_ONCE,
        CATCH_UP_BOUNDED
    }
}
