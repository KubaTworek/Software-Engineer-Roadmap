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
}
