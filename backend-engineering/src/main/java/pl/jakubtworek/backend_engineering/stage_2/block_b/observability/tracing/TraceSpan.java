package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing;

/**
 * Represents a single tracing span.
 *
 * A span should be closed after processing completes, either successfully
 * or with an error.
 */
public interface TraceSpan extends AutoCloseable {

    /**
     * Adds an attribute to the span.
     */
    void setAttribute(String key, String value);

    /**
     * Records an exception on the span.
     */
    void recordException(Throwable exception);

    /**
     * Marks the span as successful.
     */
    void markSuccess();

    /**
     * Marks the span as failed.
     */
    void markFailure();

    /**
     * Ends the span.
     */
    @Override
    void close();
}