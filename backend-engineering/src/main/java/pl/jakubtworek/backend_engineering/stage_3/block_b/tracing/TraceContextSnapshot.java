package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Immutable snapshot of the current trace context.
 *
 * This is useful for structured logs and exemplars, where trace_id and span_id
 * should be attached as correlation data without becoming Prometheus metric labels.
 */
public record TraceContextSnapshot(
        String traceId,
        String spanId,
        boolean valid
) {

    public static TraceContextSnapshot current() {
        SpanContext spanContext = Span.current().getSpanContext();

        if (spanContext == null || !spanContext.isValid()) {
            return new TraceContextSnapshot("", "", false);
        }

        return new TraceContextSnapshot(
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                true
        );
    }
}