package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.tracing;

/**
 * Console-based trace span implementation.
 */
public class ConsoleTraceSpan implements TraceSpan {

    private final String spanName;

    public ConsoleTraceSpan(String spanName) {
        this.spanName = spanName;
    }

    /**
     * Adds an attribute to the span.
     */
    @Override
    public void setAttribute(String key, String value) {
        System.out.println("span=" + spanName + ", attribute=" + key + ", value=" + value);
    }

    /**
     * Records an exception on the span.
     */
    @Override
    public void recordException(Throwable exception) {
        System.out.println("span=" + spanName + ", exception=" + exception.getMessage());
    }

    /**
     * Marks the span as successful.
     */
    @Override
    public void markSuccess() {
        System.out.println("span=" + spanName + ", status=success");
    }

    /**
     * Marks the span as failed.
     */
    @Override
    public void markFailure() {
        System.out.println("span=" + spanName + ", status=failure");
    }

    /**
     * Ends the span.
     */
    @Override
    public void close() {
        System.out.println("Closing span=" + spanName);
    }
}