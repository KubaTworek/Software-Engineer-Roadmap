package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;

/**
 * Abstraction for distributed tracing.
 *
 * In production, this would usually be implemented with OpenTelemetry.
 */
public interface TraceRecorder {

    /**
     * Starts a span for event processing.
     */
    TraceSpan startSpan(String spanName, ObservabilityContext context);
}