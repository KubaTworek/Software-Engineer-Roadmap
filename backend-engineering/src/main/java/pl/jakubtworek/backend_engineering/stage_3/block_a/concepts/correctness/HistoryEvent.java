package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.time.Instant;
import java.util.Objects;

public record HistoryEvent(
        long sequence,
        Instant at,
        String commandId,
        int attempt,
        Type type,
        long valueAfter) {

    public HistoryEvent {
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (sequence < 0 || attempt <= 0) {
            throw new IllegalArgumentException("sequence must be non-negative and attempt positive");
        }
    }

    public enum Type {
        INVOKED,
        EFFECT_APPLIED,
        DUPLICATE_SUPPRESSED,
        TIMED_OUT,
        SUCCEEDED
    }
}
