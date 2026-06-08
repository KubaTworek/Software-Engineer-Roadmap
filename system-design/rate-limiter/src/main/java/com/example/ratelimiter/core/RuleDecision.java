package com.example.ratelimiter.core;

public record RuleDecision(
        String ruleId,
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds,
        String source,
        String reason
) {}
