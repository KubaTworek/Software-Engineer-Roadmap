package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.kafka;

/**
 * Represents whether a Kafka offset has been committed.
 *
 * Tests can use this object to verify that offsets are committed only after
 * successful processing.
 */
public class TestOffsetTracker {

    private boolean committed;
    private long committedOffset = -1;

    /**
     * Commits the next offset.
     */
    public void commit(long nextOffset) {
        this.committed = true;
        this.committedOffset = nextOffset;
    }

    /**
     * Returns true when an offset has been committed.
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * Returns the last committed offset.
     */
    public long committedOffset() {
        return committedOffset;
    }

    /**
     * Clears commit state between tests.
     */
    public void reset() {
        this.committed = false;
        this.committedOffset = -1;
    }
}