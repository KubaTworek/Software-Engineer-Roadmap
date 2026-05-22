package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.dlq;

/**
 * Provides statistics about the dead-letter queue.
 *
 * Monitoring DLQ size and growth rate is critical because DLQ usually means
 * business events are not being processed correctly.
 */
public interface DeadLetterQueueStatsProvider {

    /**
     * Returns current number of messages in DLQ.
     */
    long currentSize(String dlqTopic);

    /**
     * Returns number of new DLQ messages in the selected time window.
     */
    long growthRate(String dlqTopic, java.time.Duration window);
}