package com.example.observability.server.model;

import java.time.Instant;
import java.util.Map;

public record LogQueryResult(
        String tenantId,
        Instant timestamp,
        String level,
        String service,
        String host,
        String traceId,
        String message,
        Map<String, Object> attributes
) {}
