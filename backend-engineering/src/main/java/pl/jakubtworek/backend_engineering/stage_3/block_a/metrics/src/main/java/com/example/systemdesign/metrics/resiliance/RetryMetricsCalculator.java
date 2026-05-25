package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.resiliance;

/**
 * Calculates retry health metrics.
 */
public final class RetryMetricsCalculator {

    private RetryMetricsCalculator() {
    }

    /**
     * retry_amplification = total_attempts / original_requests
     */
    public static double amplification(RetryCounters counters) {
        return (double) counters.totalAttempts() / counters.originalRequests();
    }

    /**
     * retry_success_ratio = successful_retries / retry_attempts
     */
    public static double retrySuccessRatio(RetryCounters counters) {
        long retryAttempts = counters.totalAttempts() - counters.originalRequests();

        if (retryAttempts == 0) {
            return 1.0;
        }

        return (double) counters.successfulRetries() / retryAttempts;
    }

    public static boolean retryStormRisk(RetryCounters counters) {
        return amplification(counters) > 1.20;
    }
}