package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

/**
 * Represents correlation identifiers for a single request.
 *
 * traceId identifies the whole distributed path.
 * spanId identifies the current operation within that trace.
 * requestId is an application-level ingress identifier useful for support and grep-like workflows.
 */
public record CorrelationContext(
        String requestId,
        String traceId,
        String spanId
) {

    public CorrelationContext {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }

        if (spanId != null && !spanId.isBlank() && (traceId == null || traceId.isBlank())) {
            throw new IllegalArgumentException("traceId must be present when spanId is present");
        }
    }

    public boolean hasTraceContext() {
        return traceId != null && !traceId.isBlank();
    }

    public boolean hasSpanContext() {
        return spanId != null && !spanId.isBlank();
    }
}