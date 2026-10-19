package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay;

import java.time.Instant;

/**
 * Describes how a consumer should replay historical Kafka messages.
 *
 * Replay is useful for rebuilding read models, repairing projections,
 * or backfilling newly introduced consumers.
 */
public record ReplayRequest(
        String topic,
        ReplayMode mode,
        Instant fromTimestamp
) {
    public ReplayRequest {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Replay topic cannot be empty");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Replay mode is required");
        }
        if (mode == ReplayMode.FROM_TIMESTAMP && fromTimestamp == null) {
            throw new IllegalArgumentException("Timestamp is required for timestamp replay");
        }
        if (mode != ReplayMode.FROM_TIMESTAMP && fromTimestamp != null) {
            throw new IllegalArgumentException("Timestamp is only allowed for timestamp replay");
        }
    }
}
