package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Instant;

public record SnowflakeIdComponents(
        Instant timestamp,
        int nodeId,
        int sequence
) {
}
