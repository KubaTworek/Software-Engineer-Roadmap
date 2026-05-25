package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for database-related structured log events.
 *
 * Prefer query summaries over full SQL statements to avoid leaking sensitive data
 * and to keep event attributes stable.
 */
public final class DatabaseLogEvents {

    private final ServiceResource resource;

    public DatabaseLogEvents(ServiceResource resource) {
        this.resource = resource;
    }

    public StructuredLogEvent queryCompleted(
            CorrelationContext correlationContext,
            String databaseSystemName,
            String operationName,
            String querySummary,
            String serverAddress,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("db.system.name", databaseSystemName);
        attributes.put("db.operation.name", operationName);
        attributes.put("db.query.summary", querySummary);
        attributes.put("server.address", serverAddress);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.INFO)
                .eventName(EventNames.DB_QUERY_COMPLETED)
                .body("Database query completed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }

    public StructuredLogEvent queryFailed(
            CorrelationContext correlationContext,
            String databaseSystemName,
            String operationName,
            String querySummary,
            String serverAddress,
            String errorType,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("db.system.name", databaseSystemName);
        attributes.put("db.operation.name", operationName);
        attributes.put("db.query.summary", querySummary);
        attributes.put("server.address", serverAddress);
        attributes.put("error.type", errorType);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.ERROR)
                .eventName(EventNames.DB_QUERY_FAILED)
                .body("Database query failed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }
}