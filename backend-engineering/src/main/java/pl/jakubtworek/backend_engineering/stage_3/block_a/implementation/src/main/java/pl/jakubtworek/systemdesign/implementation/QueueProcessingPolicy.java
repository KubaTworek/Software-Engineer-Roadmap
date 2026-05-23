package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;

/**
 * Describes safe queue processing constraints.
 *
 * Queues are useful only when asynchronous processing is acceptable.
 * A queue without DLQ, message age alerts, and idempotent workers is a risk.
 */
public record QueueProcessingPolicy(
        String queueName,
        boolean asynchronousProcessingAcceptable,
        boolean deadLetterQueueEnabled,
        boolean idempotentConsumer,
        Duration maxBusinessProcessingDelay,
        int maxRetryAttempts
) {
    public QueueProcessingPolicy {
        requireText(queueName, "queueName");
        if (maxBusinessProcessingDelay == null || maxBusinessProcessingDelay.isNegative() || maxBusinessProcessingDelay.isZero()) {
            throw new IllegalArgumentException("maxBusinessProcessingDelay must be positive");
        }
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be non-negative");
        }
    }

    /**
     * Detects a common anti-pattern: using a queue without operational safety mechanisms.
     */
    public boolean isUnsafeQueueDesign() {
        return !asynchronousProcessingAcceptable
                || !deadLetterQueueEnabled
                || !idempotentConsumer;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
