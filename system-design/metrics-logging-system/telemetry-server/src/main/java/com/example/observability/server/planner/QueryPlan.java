package com.example.observability.server.planner;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QueryPlan(
        String type,
        String tenantId,
        Instant start,
        Instant end,
        String storageTier,
        String tableName,
        int estimatedPartitions,
        long estimatedWindowSeconds,
        List<String> optimizations,
        Map<String, Object> filters
) {
}
