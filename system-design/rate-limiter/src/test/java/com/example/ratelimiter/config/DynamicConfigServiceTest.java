package com.example.ratelimiter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class DynamicConfigServiceTest {
    @Test void rejectsImpossibleRuleBeforeItReachesTheDataPlane() {
        var properties = new RateLimiterProperties();
        var validator = new RuleConfigurationValidator(properties);
        var impossible = rule("export", 20, 50);
        assertThatThrownBy(() -> validator.validate(impossible))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..capacity");
    }

    @Test void updateRequiresCurrentVersionAndProducesAuditEntry() {
        var properties = new RateLimiterProperties();
        var service = new DynamicConfigService(properties);
        assertThat(service.upsertRule(rule("login", 10, 1), 1, "operator-7")).isEqualTo(2);
        assertThat(service.audit()).singleElement().satisfies(entry -> {
            assertThat(entry.ruleId()).isEqualTo("login");
            assertThat(entry.actor()).isEqualTo("operator-7");
        });
        assertThatThrownBy(() -> service.disableRule("login", 1, "stale-operator"))
                .isInstanceOf(ConfigVersionConflictException.class);
    }

    @Test void rejectsUnsupportedMethodAndUnboundedIdentifiers() {
        var properties = new RateLimiterProperties();
        var validator = new RuleConfigurationValidator(properties);
        var endpoint = rule("x".repeat(101), 10, 1);
        endpoint.setType(RateLimiterProperties.RuleType.ENDPOINT);
        endpoint.setPathPattern("/api/items");
        endpoint.setMethod("BREW");
        assertThatThrownBy(() -> validator.validate(endpoint))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds 100");
        endpoint.setId("items");
        assertThatThrownBy(() -> validator.validate(endpoint))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported HTTP method");
    }

    private RateLimiterProperties.Rule rule(String id, long capacity, long cost) {
        var rule = new RateLimiterProperties.Rule();
        rule.setId(id); rule.setCapacity(capacity); rule.setCost(cost); rule.setRefillTokensPerSecond(1);
        return rule;
    }
}
