package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.quota.QuotaService;
import com.example.ratelimiter.usage.UsageEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
                new SimpleMeterRegistry()
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
