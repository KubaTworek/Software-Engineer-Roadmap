package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.Objects;

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
        this.span = Objects.requireNonNull(span, "span must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
    }

    public Span span() {
        return span;
    }

    @Override
    public synchronized void close() {
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
