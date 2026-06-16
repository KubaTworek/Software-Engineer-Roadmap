package com.example.observability.server.model;

import java.time.Instant;
import java.util.Map;

public record TraceSpanResult(
        String tenantId,
        String traceId,
        String spanId,
        String parentSpanId,
        String service,
        String operation,
        Instant startTime,
        Instant endTime,
        double durationMs,
        String status,
        Map<String, Object> attributes
) {
}
