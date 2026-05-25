package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.resiliance;

/**
 * Runtime counters for retry behavior.
 */
public record RetryCounters(
        long originalRequests,
        long totalAttempts,
        long successfulRetries
) {
    public RetryCounters {
        if (originalRequests <= 0) throw new IllegalArgumentException("originalRequests must be positive");
        if (totalAttempts < originalRequests) throw new IllegalArgumentException("totalAttempts cannot be lower than originalRequests");
        if (successfulRetries < 0) throw new IllegalArgumentException("successfulRetries must be non-negative");
    }
}