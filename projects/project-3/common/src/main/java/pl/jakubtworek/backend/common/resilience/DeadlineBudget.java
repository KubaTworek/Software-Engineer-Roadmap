package pl.jakubtworek.backend.common.resilience;

import java.time.Duration;
import java.time.Instant;

/**
 * End-to-end deadline ogranicza cały request, podczas gdy timeout klienta ogranicza
 * pojedynczą próbę. Każdy downstream otrzymuje tylko pozostały budżet; retry nie może
 * resetować zegara i przedłużać pracy poza obietnicę daną klientowi.
 */
public record DeadlineBudget(Instant deadline) {
    public Duration remainingAt(Instant now) {
        Duration remaining = Duration.between(now, deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public Duration attemptTimeout(Instant now, Duration configuredMaximum, Duration reserveForCleanup) {
        Duration usable = remainingAt(now).minus(reserveForCleanup);
        if (usable.isNegative() || usable.isZero()) {
            throw new DeadlineExceededException();
        }
        return usable.compareTo(configuredMaximum) < 0 ? usable : configuredMaximum;
    }

    public static final class DeadlineExceededException extends RuntimeException { }
}
