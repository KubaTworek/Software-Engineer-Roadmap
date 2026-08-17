package com.example.ratelimiter.core;

import java.util.List;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds,
        List<RuleDecision> ruleDecisions
) {
    public static RateLimitDecision from(List<RuleDecision> decisions) {
        boolean allowed = decisions.stream().allMatch(RuleDecision::allowed);
        long limit = decisions.stream().mapToLong(RuleDecision::limit).min().orElse(0);
        long remaining = decisions.stream().mapToLong(RuleDecision::remaining).min().orElse(0);
        long retry = decisions.stream().mapToLong(RuleDecision::retryAfterSeconds).max().orElse(0);
        return new RateLimitDecision(allowed, limit, remaining, retry, decisions);
    }
}
