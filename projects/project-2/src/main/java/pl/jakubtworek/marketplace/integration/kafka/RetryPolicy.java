package pl.jakubtworek.marketplace.integration.kafka;

import pl.jakubtworek.marketplace.integration.contracts.UnsupportedEventVersionException;

import java.time.Duration;
import java.util.UUID;

/**
 * Retry jest decyzją o charakterze błędu, nie automatyczną reakcją na Exception.
 * Błędy kontraktu i walidacji są permanentne i powinny od razu trafić do DLQ.
 * Błędy przejściowe dostają ograniczoną liczbę prób oraz exponential backoff z
 * deterministycznym jitterem, aby wiele instancji nie ponawiało jednocześnie.
 * Worker laboratoryjny eksponuje wyliczone opóźnienie; w produkcji należy umieścić
 * wiadomość w retry topicu, zamiast blokować partycję przez Thread.sleep().
 */
public final class RetryPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    public RetryPolicy(int maxAttempts) { this(maxAttempts, Duration.ofMillis(100), Duration.ofSeconds(10)); }

    public RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public boolean shouldRetry(Throwable failure, int attempts) {
        return attempts < maxAttempts
                && !(failure instanceof IllegalArgumentException)
                && !(failure instanceof UnsupportedEventVersionException);
    }

    public Duration delayFor(int attempts, UUID eventId) {
        long exponential = baseDelay.toMillis() * (1L << Math.min(attempts - 1, 20));
        long capped = Math.min(exponential, maxDelay.toMillis());
        long jitter = Math.floorMod(eventId.getLeastSignificantBits(), Math.max(1L, capped / 4 + 1));
        return Duration.ofMillis(Math.min(maxDelay.toMillis(), capped + jitter));
    }

    public int maxAttempts() { return maxAttempts; }
    public static RetryPolicy defaultPolicy() { return new RetryPolicy(3); }
}
