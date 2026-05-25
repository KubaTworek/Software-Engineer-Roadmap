package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Represents a started span and its context scope.
 *
 * Use try-with-resources to guarantee that the span and scope are closed correctly.
 */
public final class SpanScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;
    private boolean closed;

    public SpanScope(Span span, Scope scope) {
        this.span = span;
        this.scope = scope;
    }

    public Span span() {
        return span;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                scope.close();
            } finally {
                span.end();
                closed = true;
            }
        }
    }
}