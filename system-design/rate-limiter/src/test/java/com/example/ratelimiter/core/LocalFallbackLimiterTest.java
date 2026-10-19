package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFallbackLimiterTest {

    @Test
    void shouldDenyWhenLocalFallbackWindowLimitIsExceeded() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getLocalFallback().setDefaultLimit(2);
        properties.getLocalFallback().setDefaultWindowSeconds(60);

        LocalFallbackLimiter limiter = new LocalFallbackLimiter(properties);
        RateLimiterProperties.Rule rule = new RateLimiterProperties.Rule();
        rule.setId("user-limit");
        rule.setCapacity(100);

        RequestContext ctx = new RequestContext(
                "GET", "/api/users", "203.0.113.10", null,
                "user-1", "tenant-1", "FREE", 1_700_000_000_000L
        );

        RuleDecision first = limiter.consume(rule, ctx, "REDIS_UNAVAILABLE");
        RuleDecision second = limiter.consume(rule, ctx, "REDIS_UNAVAILABLE");
        RuleDecision third = limiter.consume(rule, ctx, "REDIS_UNAVAILABLE");

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isZero();
        assertThat(third.retryAfterSeconds()).isPositive();
        assertThat(third.source()).isEqualTo("local-fallback");
    }

    @Test
    void shouldChargeConfiguredCostInsteadOfCountingEveryOperationAsOne() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getLocalFallback().setDefaultLimit(10);
        RateLimiterProperties.Rule rule = new RateLimiterProperties.Rule();
        rule.setId("export"); rule.setCapacity(10); rule.setCost(4);
        LocalFallbackLimiter limiter = new LocalFallbackLimiter(properties);
        RequestContext context = new RequestContext("POST", "/api/exports", "203.0.113.1", null,
                "user", "tenant", "FREE", 1_700_000_000_000L);

        assertThat(limiter.consume(rule, context, "REDIS_UNAVAILABLE").allowed()).isTrue();
        assertThat(limiter.consume(rule, context, "REDIS_UNAVAILABLE").allowed()).isTrue();
        assertThat(limiter.consume(rule, context, "REDIS_UNAVAILABLE").allowed()).isFalse();
    }

    @Test
    void rejectsNewPrincipalsAfterBoundedFallbackStateIsExhausted() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getLocalFallback().setMaxBuckets(1);
        LocalFallbackLimiter limiter = new LocalFallbackLimiter(properties);
        RateLimiterProperties.Rule rule = new RateLimiterProperties.Rule();
        rule.setId("user-limit");

        assertThat(limiter.consume(rule, context("first"), "REDIS_UNAVAILABLE").allowed()).isTrue();
        RuleDecision rejected = limiter.consume(rule, context("second"), "REDIS_UNAVAILABLE");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo("FALLBACK_CARDINALITY_LIMIT");
    }

    private RequestContext context(String user) {
        return new RequestContext("GET", "/api/users", "203.0.113.10", null,
                user, "tenant", "FREE", 1_700_000_000_000L);
    }
}
