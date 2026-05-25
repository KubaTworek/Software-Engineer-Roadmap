package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for HTTP-related structured log events.
 *
 * The route should be a low-cardinality route template, for example "/orders/:id/pay",
 * not a raw path such as "/orders/123/pay".
 */
public final class HttpLogEvents {

    private final ServiceResource resource;

    public HttpLogEvents(ServiceResource resource) {
        this.resource = resource;
    }

    public StructuredLogEvent requestCompleted(
            CorrelationContext correlationContext,
            String method,
            String route,
            int statusCode,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("http.request.method", method);
        attributes.put("http.route", route);
        attributes.put("http.response.status_code", statusCode);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.INFO)
                .eventName(EventNames.HTTP_REQUEST_COMPLETED)
                .body("HTTP request completed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }

    public StructuredLogEvent requestFailed(
            CorrelationContext correlationContext,
            String method,
            String route,
            int statusCode,
            String errorType,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("http.request.method", method);
        attributes.put("http.route", route);
        attributes.put("http.response.status_code", statusCode);
        attributes.put("error.type", errorType);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.ERROR)
                .eventName(EventNames.HTTP_REQUEST_FAILED)
                .body("HTTP request failed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }
}