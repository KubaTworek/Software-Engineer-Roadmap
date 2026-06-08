package com.example.ratelimiter.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitDecisionTest {

    @Test
    void shouldAllowOnlyWhenAllRulesAllow() {
        RateLimitDecision decision = RateLimitDecision.from(List.of(
                new RuleDecision("global", true, 100_000, 99_999, 0, "redis", "OK"),
                new RuleDecision("tenant", true, 10_000, 9_999, 0, "redis", "OK"),
                new RuleDecision("endpoint", false, 20, 0, 17, "redis", "RATE_LIMIT_EXCEEDED")
        ));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(20);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void shouldUseMostRestrictiveValuesForAggregatedHeaders() {
        RateLimitDecision decision = RateLimitDecision.from(List.of(
                new RuleDecision("global", true, 100_000, 90_000, 0, "redis", "OK"),
                new RuleDecision("tenant", true, 10_000, 100, 0, "redis", "OK"),
                new RuleDecision("user", true, 100, 7, 0, "redis", "OK")
        ));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(100);
        assertThat(decision.remaining()).isEqualTo(7);
        assertThat(decision.retryAfterSeconds()).isZero();
    }
}
