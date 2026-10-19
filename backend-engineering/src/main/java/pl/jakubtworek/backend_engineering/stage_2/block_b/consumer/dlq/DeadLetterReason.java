package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq;

import java.time.Instant;
import java.time.Clock;

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
        int attempts,
        Instant failedAt
) {
    public DeadLetterReason {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt must not be null");
        }
    }

    /**
     * Creates a DLQ reason from an exception.
     */
    public static DeadLetterReason fromException(
            String errorCode,
            Exception exception
    ) {
        return fromException(errorCode, exception, 1, Clock.systemUTC());
    }

    public static DeadLetterReason fromException(
            String errorCode,
            Exception exception,
            int attempts,
            Clock clock
    ) {
        return new DeadLetterReason(
                errorCode,
                exception.getMessage(),
                exception.getClass().getName(),
                attempts,
                clock.instant()
        );
    }
}
