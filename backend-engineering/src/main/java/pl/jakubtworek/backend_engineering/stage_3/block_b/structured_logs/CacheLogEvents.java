package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for Redis/cache-related structured log events.
 *
 * The goal is to identify whether cache behavior helps or hurts the request path.
 */
public final class CacheLogEvents {

    private final ServiceResource resource;

    public CacheLogEvents(ServiceResource resource) {
        this.resource = resource;
    }

    public StructuredLogEvent redisLookupCompleted(
            CorrelationContext correlationContext,
            String operationName,
            String serverAddress,
            boolean cacheHit,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("db.system.name", "redis");
        attributes.put("db.operation.name", operationName);
        attributes.put("server.address", serverAddress);
        attributes.put("cache.hit", cacheHit);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.INFO)
                .eventName(EventNames.CACHE_LOOKUP)
                .body("Redis lookup completed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }

    public StructuredLogEvent redisLookupFailed(
            CorrelationContext correlationContext,
            String operationName,
            String serverAddress,
            String errorType,
            long durationMs
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("db.system.name", "redis");
        attributes.put("db.operation.name", operationName);
        attributes.put("server.address", serverAddress);
        attributes.put("error.type", errorType);
        attributes.put("duration_ms", durationMs);

        return StructuredLogEvent.builder(resource)
                .severity(LogSeverity.ERROR)
                .eventName(EventNames.CACHE_LOOKUP_FAILED)
                .body("Redis lookup failed")
                .correlation(correlationContext)
                .attributes(attributes)
                .build();
    }
}