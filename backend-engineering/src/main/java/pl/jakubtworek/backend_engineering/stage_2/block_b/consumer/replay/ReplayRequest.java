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
}