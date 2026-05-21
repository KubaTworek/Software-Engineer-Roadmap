package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.retry;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.logging.StructuredLogger;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.EventProcessingMetrics;

import java.util.Map;

/**
 * Records observability signals related to retry attempts.
 *
 * Retry monitoring is important because excessive retries may indicate
 * downstream instability or incorrect error classification.
 */
public class RetryObserver {

    private final StructuredLogger logger;
    private final EventProcessingMetrics metrics;

    public RetryObserver(
            StructuredLogger logger,
            EventProcessingMetrics metrics
    ) {
        this.logger = logger;
        this.metrics = metrics;
    }

    /**
     * Records a retry attempt.
     */
    public void onRetry(
            ObservabilityContext context,
            int attemptNumber,
            Throwable exception
    ) {
        metrics.recordRetry(context, attemptNumber);

        logger.warn(
                "Retrying event processing.",
                context,
                Map.of(
                        "attempt", attemptNumber,
                        "exceptionClass", exception.getClass().getName(),
                        "exceptionMessage", exception.getMessage()
                )
        );
    }
}