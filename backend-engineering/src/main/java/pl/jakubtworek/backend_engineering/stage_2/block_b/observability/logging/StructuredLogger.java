package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.logging;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;

import java.util.Map;

/**
 * Abstraction for structured application logging.
 *
 * Logs should include correlationId and eventId so they can be searched
 * across multiple services involved in the same business process.
 */
public interface StructuredLogger {

    /**
     * Logs an informational message with diagnostic context.
     */
    void info(String message, ObservabilityContext context, Map<String, Object> fields);

    /**
     * Logs a warning message with diagnostic context.
     */
    void warn(String message, ObservabilityContext context, Map<String, Object> fields);

    /**
     * Logs an error message with diagnostic context and exception details.
     */
    void error(String message, ObservabilityContext context, Throwable exception, Map<String, Object> fields);
}