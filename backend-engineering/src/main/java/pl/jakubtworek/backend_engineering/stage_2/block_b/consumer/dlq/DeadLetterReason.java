package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq;

import java.time.Instant;

/**
 * Describes why an event was sent to the dead-letter topic.
 *
 * The reason should contain enough context to debug and potentially replay
 * the failed message later.
 */
public record DeadLetterReason(
        String errorCode,
        String message,
        String exceptionClass,
        Instant failedAt
) {
    /**
     * Creates a DLQ reason from an exception.
     */
    public static DeadLetterReason fromException(
            String errorCode,
            Exception exception
    ) {
        return new DeadLetterReason(
                errorCode,
                exception.getMessage(),
                exception.getClass().getName(),
                Instant.now()
        );
    }
}