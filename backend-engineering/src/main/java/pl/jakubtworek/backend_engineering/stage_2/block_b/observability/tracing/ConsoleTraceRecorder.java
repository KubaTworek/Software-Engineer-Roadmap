package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;

/**
 * Simple tracing implementation that prints span lifecycle events.
 *
 * This class shows the shape of tracing logic without binding the code
 * to a specific observability vendor.
 */
public class ConsoleTraceRecorder implements TraceRecorder {

    /**
     * Starts a new trace span.
     */
    @Override
    public TraceSpan startSpan(String spanName, ObservabilityContext context) {
        System.out.println(
                "Starting span=" + spanName
                        + ", traceId=" + context.traceId()
                        + ", correlationId=" + context.correlationId()
        );

        return new ConsoleTraceSpan(spanName);
    }
}