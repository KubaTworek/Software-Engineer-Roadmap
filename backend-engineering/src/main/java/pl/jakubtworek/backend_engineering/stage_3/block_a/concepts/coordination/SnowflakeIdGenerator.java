package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * A small Snowflake-style generator: 41 timestamp bits, 10 node bits and
 * 12 sequence bits. It fails fast on clock regression instead of silently
 * generating an identifier that may collide with an earlier one.
 */
public final class SnowflakeIdGenerator {

    private static final int SEQUENCE_BITS = 12;
    private static final int NODE_BITS = 10;
    private static final int NODE_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = NODE_BITS + SEQUENCE_BITS;
    private static final int MAX_NODE_ID = (1 << NODE_BITS) - 1;
    private static final int MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1;
    private static final long MAX_TIMESTAMP_DELTA = (1L << 41) - 1;

    private final int nodeId;
    private final long epochMillis;
    private final Clock clock;

    private long lastTimestamp = -1;
    private int sequence;

    public SnowflakeIdGenerator(int nodeId, Instant epoch, Clock clock) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
        this.epochMillis = Objects.requireNonNull(epoch, "epoch must not be null").toEpochMilli();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized long nextId() {
        long timestamp = clock.millis();
        if (timestamp < epochMillis) {
            throw new IllegalStateException("clock is before the configured epoch");
        }
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("clock moved backwards; refusing to risk an ID collision");
        }

        if (timestamp == lastTimestamp) {
            if (sequence == MAX_SEQUENCE) {
                throw new IllegalStateException("sequence exhausted for the current millisecond");
            }
            sequence++;
        } else {
            lastTimestamp = timestamp;
            sequence = 0;
        }

        long delta = timestamp - epochMillis;
        if (delta > MAX_TIMESTAMP_DELTA) {
            throw new IllegalStateException("timestamp no longer fits in 41 bits");
        }

        return (delta << TIMESTAMP_SHIFT)
                | ((long) nodeId << NODE_SHIFT)
                | sequence;
    }

    public SnowflakeIdComponents decode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        long timestampDelta = id >>> TIMESTAMP_SHIFT;
        int decodedNode = (int) ((id >>> NODE_SHIFT) & MAX_NODE_ID);
        int decodedSequence = (int) (id & MAX_SEQUENCE);
        return new SnowflakeIdComponents(
                Instant.ofEpochMilli(epochMillis + timestampDelta),
                decodedNode,
                decodedSequence
        );
    }
}
