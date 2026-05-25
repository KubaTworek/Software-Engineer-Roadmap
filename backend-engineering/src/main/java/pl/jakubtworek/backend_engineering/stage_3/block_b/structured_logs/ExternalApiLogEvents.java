package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for external dependency events.
 *
 * These logs help distinguish local application latency from downstream latency.
 */
public final class ExternalApiLogEvents {

    private final ServiceResource resource;

    public ExternalApiLogEvents(ServiceResource resource) {
        this.resource = resource;
    }

    public StructuredLogEvent requestCompleted(
            CorrelationContext correlationContext,
            String dependencyName,
            String method,
            String route,
            int statusCode,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("dependency.name", dependencyName);
        attributes.put("http.request.method", method);
        attributes.put("http.route", route);
        attributes.put("http.response.status_code", statusCode);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.INFO)
                .eventName(EventNames.EXTERNAL_API_REQUEST_COMPLETED)
                .body("External API request completed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }

    public StructuredLogEvent requestFailed(
            CorrelationContext correlationContext,
            String dependencyName,
            String method,
            String route,
            int statusCode,
            String errorType,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("dependency.name", dependencyName);
        attributes.put("http.request.method", method);
        attributes.put("http.route", route);
        attributes.put("http.response.status_code", statusCode);
        attributes.put("error.type", errorType);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.ERROR)
                .eventName(EventNames.EXTERNAL_API_REQUEST_FAILED)
                .body("External API request failed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }
}