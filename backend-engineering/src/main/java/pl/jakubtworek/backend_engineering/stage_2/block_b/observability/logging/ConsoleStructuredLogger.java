package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.logging;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple structured logger used for demonstration purposes.
 *
 * A production implementation would usually delegate to SLF4J, Logback,
 * Log4j2 or another logging framework configured for JSON logs.
 */
public class ConsoleStructuredLogger implements StructuredLogger {

    /**
     * Logs an informational message as a structured map.
     */
    @Override
    public void info(
            String message,
            ObservabilityContext context,
            Map<String, Object> fields
    ) {
        log("INFO", message, context, null, fields);
    }

    /**
     * Logs a warning message as a structured map.
     */
    @Override
    public void warn(
            String message,
            ObservabilityContext context,
            Map<String, Object> fields
    ) {
        log("WARN", message, context, null, fields);
    }

    /**
     * Logs an error message as a structured map with exception details.
     */
    @Override
    public void error(
            String message,
            ObservabilityContext context,
            Throwable exception,
            Map<String, Object> fields
    ) {
        log("ERROR", message, context, exception, fields);
    }

    /**
     * Builds a structured log record.
     */
    private void log(
            String level,
            String message,
            ObservabilityContext context,
            Throwable exception,
            Map<String, Object> fields
    ) {
        Map<String, Object> logRecord = new HashMap<>();

        logRecord.put("level", level);
        logRecord.put("message", message);
        logRecord.put("eventId", context.eventId());
        logRecord.put("correlationId", context.correlationId());
        logRecord.put("causationId", context.causationId());
        logRecord.put("sourceService", context.sourceService());
        logRecord.put("eventType", context.eventType());
        logRecord.put("aggregateId", context.aggregateId());
        logRecord.put("traceId", context.traceId());

        if (exception != null) {
            logRecord.put("exceptionClass", exception.getClass().getName());
            logRecord.put("exceptionMessage", exception.getMessage());
        }

        if (fields != null) {
            logRecord.putAll(fields);
        }

        System.out.println(logRecord);
    }
}