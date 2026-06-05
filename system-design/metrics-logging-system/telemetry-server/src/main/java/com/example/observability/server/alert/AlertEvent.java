package com.example.observability.server.alert;

import java.time.Instant;

public record AlertEvent(
        String tenantId,
        String ruleId,
        String ruleName,
        String status,
        Instant evaluatedAt,
        double observedValue,
        double threshold,
        String message
) {}
