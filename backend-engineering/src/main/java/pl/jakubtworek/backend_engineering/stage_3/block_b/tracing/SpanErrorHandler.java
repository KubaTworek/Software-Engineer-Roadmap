package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * Applies a consistent error policy to spans.
 *
 * Recording exceptions on spans helps tail sampling policies retain failed traces.
 */
public final class SpanErrorHandler {

    private SpanErrorHandler() {
    }

    public static void recordException(Span span, Throwable throwable) {
        if (span == null || throwable == null) {
            return;
        }

        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR, throwable.getMessage());
        span.setAttribute(TracingAttributes.ERROR_TYPE, throwable.getClass().getSimpleName());
    }
}