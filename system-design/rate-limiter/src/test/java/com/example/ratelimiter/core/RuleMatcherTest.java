package com.example.ratelimiter.core;

import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.RateLimiterProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMatcherTest {

    @Test
    void shouldMatchMultipleRulesAndKeepPriorityOrder() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setRules(List.of(
                rule("plan-free", RateLimiterProperties.RuleType.PLAN, 40, r -> r.setPlan("FREE")),
                rule("global", RateLimiterProperties.RuleType.GLOBAL, 10, r -> {}),
                rule("tenant-t1", RateLimiterProperties.RuleType.TENANT, 20, r -> r.setTenantId("tenant-1")),
                rule("user-u1", RateLimiterProperties.RuleType.USER, 30, r -> r.setUserId("user-1")),
                rule("endpoint-payments", RateLimiterProperties.RuleType.ENDPOINT, 25, r -> {
                    r.setMethod("POST");
                    r.setPathPattern("/api/payments");
                }),
                rule("disabled", RateLimiterProperties.RuleType.GLOBAL, 1, r -> r.setEnabled(false))
        ));

        RuleMatcher matcher = new RuleMatcher(new DynamicConfigService(properties));
        RequestContext ctx = new RequestContext(
                "POST", "/api/payments", "203.0.113.10", null,
                "user-1", "tenant-1", "FREE", 1_700_000_000_000L
        );

        assertThat(matcher.match(ctx))
                .extracting(RateLimiterProperties.Rule::getId)
                .containsExactly("global", "tenant-t1", "endpoint-payments", "user-u1", "plan-free");
    }

    @Test
    void shouldMatchWildcardEndpointPattern() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setRules(List.of(
                rule("api-wildcard", RateLimiterProperties.RuleType.ENDPOINT, 10, r -> {
                    r.setMethod("GET");
                    r.setPathPattern("/api/**");
                })
        ));

        RuleMatcher matcher = new RuleMatcher(new DynamicConfigService(properties));
        RequestContext ctx = new RequestContext(
                "GET", "/api/users/123", "203.0.113.10", null,
                null, null, "FREE", 1_700_000_000_000L
        );

        assertThat(matcher.match(ctx)).extracting(RateLimiterProperties.Rule::getId).containsExactly("api-wildcard");
    }

    private RateLimiterProperties.Rule rule(
            String id,
            RateLimiterProperties.RuleType type,
            int priority,
            java.util.function.Consumer<RateLimiterProperties.Rule> customizer
    ) {
        RateLimiterProperties.Rule rule = new RateLimiterProperties.Rule();
        rule.setId(id);
        rule.setType(type);
        rule.setPriority(priority);
        rule.setCapacity(100);
        rule.setRefillTokensPerSecond(10);
        customizer.accept(rule);
        return rule;
    }
}
