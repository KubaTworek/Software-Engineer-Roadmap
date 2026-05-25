package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.queue;

import java.time.Duration;

/**
 * Runtime queue metrics used to detect backlog and processing risk.
 */
public record QueueMetrics(
        long queueDepth,
        Duration oldestMessageAge,
        long consumerLag,
        long dlqMessages,
        double workerFailureRate
) {
    public QueueMetrics {
        if (queueDepth < 0) throw new IllegalArgumentException("queueDepth must be non-negative");
        if (oldestMessageAge == null || oldestMessageAge.isNegative()) throw new IllegalArgumentException("oldestMessageAge must be non-negative");
        if (consumerLag < 0) throw new IllegalArgumentException("consumerLag must be non-negative");
        if (dlqMessages < 0) throw new IllegalArgumentException("dlqMessages must be non-negative");
        if (workerFailureRate < 0 || workerFailureRate > 1) throw new IllegalArgumentException("workerFailureRate must be in range [0, 1]");
    }
}