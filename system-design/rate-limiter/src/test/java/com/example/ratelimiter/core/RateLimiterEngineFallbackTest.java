package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.quota.QuotaService;
import com.example.ratelimiter.usage.UsageEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimiterEngineFallbackTest {

    @Test
    void shouldUseLocalFallbackWhenRedisFailsAndStrategyIsLocalFallback() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.LOCAL_FALLBACK);
        properties.getLocalFallback().setDefaultLimit(1);
        RateLimiterProperties.Rule rule = rule("user-limit", RateLimiterProperties.FailureStrategy.LOCAL_FALLBACK);

        RateLimiterEngine engine = engineWithFailingRedis(properties, rule);
        RequestContext ctx = ctx();

        RateLimitDecision first = engine.check(ctx);
        RateLimitDecision second = engine.check(ctx);

        assertThat(first.allowed()).isTrue();
        assertThat(first.ruleDecisions()).extracting(RuleDecision::source).containsExactly("local-fallback");
        assertThat(second.allowed()).isFalse();
        assertThat(second.ruleDecisions()).extracting(RuleDecision::source).containsExactly("local-fallback");
    }

    @Test
    void shouldFailOpenWhenRuleUsesFailOpenStrategy() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RateLimiterProperties.Rule rule = rule("global", RateLimiterProperties.FailureStrategy.FAIL_OPEN);

        RateLimitDecision decision = engineWithFailingRedis(properties, rule).check(ctx());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.ruleDecisions().getFirst().source()).isEqualTo("fail-open");
    }

    @Test
    void shouldFailClosedWhenRuleUsesFailClosedStrategy() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.FAIL_OPEN);
        RateLimiterProperties.Rule rule = rule("global", RateLimiterProperties.FailureStrategy.FAIL_CLOSED);

        RateLimitDecision decision = engineWithFailingRedis(properties, rule).check(ctx());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(30);
        assertThat(decision.ruleDecisions().getFirst().source()).isEqualTo("fail-closed");
    }

    @Test
    void rejectedAttemptStillConsumesEveryMatchingProtectionBudget() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RateLimiterProperties.Rule global = rule("global", RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RateLimiterProperties.Rule endpoint = rule("endpoint", RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RuleMatcher matcher = mock(RuleMatcher.class);
        when(matcher.match(any())).thenReturn(List.of(global, endpoint));
        RedisTokenBucketLimiter redis = mock(RedisTokenBucketLimiter.class);
        when(redis.consume(global, ctx())).thenReturn(new RuleDecision("global", true, 10, 9, 0, "redis", "OK"));
        when(redis.consume(endpoint, ctx())).thenReturn(new RuleDecision("endpoint", false, 1, 0, 1, "redis", "LIMIT"));
        RateLimiterEngine engine = new RateLimiterEngine(matcher, redis, new LocalFallbackLimiter(properties),
                properties, mock(UsageEventPublisher.class), mock(QuotaService.class), new SimpleMeterRegistry(),
                ObservationRegistry.NOOP);

        assertThat(engine.check(ctx()).allowed()).isFalse();
        verify(redis).consume(global, ctx());
        verify(redis).consume(endpoint, ctx());
    }

    @Test
    void onlineQuotaFailureMustNotTurnAnAllowedRequestIntoAnError() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RateLimiterProperties.Rule rule = rule("global", RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RuleMatcher matcher = mock(RuleMatcher.class);
        when(matcher.match(any())).thenReturn(List.of(rule));
        RedisTokenBucketLimiter redis = mock(RedisTokenBucketLimiter.class);
        when(redis.consume(any(), any())).thenReturn(new RuleDecision("global", true, 10, 9, 0, "redis", "OK"));
        QuotaService quota = mock(QuotaService.class);
        doThrow(new RuntimeException("quota Redis unavailable")).when(quota).recordAcceptedUsage(any(), anyLong());
        RateLimiterEngine engine = new RateLimiterEngine(matcher, redis, new LocalFallbackLimiter(properties),
                properties, mock(UsageEventPublisher.class), quota, new SimpleMeterRegistry(), ObservationRegistry.NOOP);

        assertThat(engine.check(ctx()).allowed()).isTrue();
    }

    @Test
    void shadowRuleReportsWouldDenyWithoutBlockingTraffic() {
        RateLimiterProperties properties = baseProperties(RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        RateLimiterProperties.Rule rule = rule("candidate", RateLimiterProperties.FailureStrategy.FAIL_CLOSED);
        rule.setEnforcementMode(RateLimiterProperties.EnforcementMode.MONITOR);
        RuleMatcher matcher = mock(RuleMatcher.class);
        when(matcher.match(any())).thenReturn(List.of(rule));
        RedisTokenBucketLimiter redis = mock(RedisTokenBucketLimiter.class);
        when(redis.consume(any(), any())).thenReturn(
                new RuleDecision("candidate", false, 10, 0, 5, "redis", "LIMIT"));
        RateLimiterEngine engine = new RateLimiterEngine(matcher, redis, new LocalFallbackLimiter(properties),
                properties, mock(UsageEventPublisher.class), mock(QuotaService.class),
                new SimpleMeterRegistry(), ObservationRegistry.NOOP);

        RateLimitDecision result = engine.check(ctx());

        assertThat(result.allowed()).isTrue();
        assertThat(result.ruleDecisions()).singleElement()
                .extracting(RuleDecision::reason).isEqualTo("SHADOW_WOULD_DENY");
    }

    private RateLimiterEngine engineWithFailingRedis(RateLimiterProperties properties, RateLimiterProperties.Rule rule) {
        RuleMatcher matcher = mock(RuleMatcher.class);
        when(matcher.match(any())).thenReturn(List.of(rule));

        RedisTokenBucketLimiter redis = mock(RedisTokenBucketLimiter.class);
        when(redis.consume(any(), any())).thenThrow(new RuntimeException("redis down"));

        UsageEventPublisher usage = mock(UsageEventPublisher.class);
        QuotaService quota = mock(QuotaService.class);

        return new RateLimiterEngine(
                matcher,
                redis,
                new LocalFallbackLimiter(properties),
                properties,
                usage,
                quota,
                new SimpleMeterRegistry(),
                ObservationRegistry.NOOP
        );
    }

    private RateLimiterProperties baseProperties(RateLimiterProperties.FailureStrategy defaultStrategy) {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultFailureStrategy(defaultStrategy);
        properties.getUsageEvents().setEnabled(false);
        properties.getQuotas().setEnabled(false);
        return properties;
    }

    private RateLimiterProperties.Rule rule(String id, RateLimiterProperties.FailureStrategy strategy) {
        RateLimiterProperties.Rule rule = new RateLimiterProperties.Rule();
        rule.setId(id);
        rule.setType(RateLimiterProperties.RuleType.USER);
        rule.setUserId("user-1");
        rule.setCapacity(10);
        rule.setRefillTokensPerSecond(1);
        rule.setCost(1);
        rule.setFailureStrategy(strategy);
        return rule;
    }

    private RequestContext ctx() {
        return new RequestContext(
                "GET", "/api/users", "203.0.113.10", null,
                "user-1", "tenant-1", "FREE", 1_700_000_000_000L
        );
    }
}
