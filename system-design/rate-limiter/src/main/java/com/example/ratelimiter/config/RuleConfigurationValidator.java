package com.example.ratelimiter.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Fails at startup instead of installing a rule that can never admit traffic.
 * Bean Validation checks individual numbers; these checks cover relationships
 * between fields and identity required by a particular scope.
 */
@Component
public class RuleConfigurationValidator implements SmartInitializingSingleton {
    private static final int MAX_RULES = 10_000;
    private static final int MAX_ID_LENGTH = 100;
    private static final int MAX_PATH_LENGTH = 500;
    private static final Pattern HTTP_METHOD = Pattern.compile("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS");
    private final RateLimiterProperties properties;

    public RuleConfigurationValidator(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(properties.getRules());
        if (properties.getLocalFallback().getDefaultLimit() <= 0
                || properties.getLocalFallback().getDefaultWindowSeconds() <= 0
                || properties.getLocalFallback().getMaxBuckets() <= 0) {
            throw new IllegalStateException("Local fallback limit, window and maxBuckets must be positive");
        }
    }

    public void validate(List<RateLimiterProperties.Rule> rules) {
        if (rules.size() > MAX_RULES) fail("at most " + MAX_RULES + " rules are supported");
        Set<String> ids = new HashSet<>();
        for (RateLimiterProperties.Rule rule : rules) validate(rule, ids);
    }

    public void validate(RateLimiterProperties.Rule rule) {
        validate(rule, new HashSet<>());
    }

    private void validate(RateLimiterProperties.Rule rule, Set<String> ids) {
        if (rule.getId() == null || rule.getId().isBlank()) fail("rule id is required");
        if (rule.getId().length() > MAX_ID_LENGTH) fail("rule id exceeds " + MAX_ID_LENGTH + " characters");
        if (!ids.add(rule.getId())) fail("duplicate rule id: " + rule.getId());
        if (rule.getCapacity() <= 0) fail(rule.getId() + ": capacity must be positive");
        if (!Double.isFinite(rule.getRefillTokensPerSecond()) || rule.getRefillTokensPerSecond() <= 0) {
            fail(rule.getId() + ": refill rate must be finite and positive");
        }
        if (rule.getCost() <= 0 || rule.getCost() > rule.getCapacity()) {
            fail(rule.getId() + ": cost must be in range 1..capacity");
        }
        if (rule.getType() == null || rule.getAlgorithm() == null || rule.getRegionMode() == null
                || rule.getEnforcementMode() == null) {
            fail(rule.getId() + ": type, algorithm, region mode and enforcement mode are required");
        }
        switch (rule.getType()) {
            case TENANT -> required(rule.getTenantId(), rule.getId(), "tenantId");
            case USER -> required(rule.getUserId(), rule.getId(), "userId");
            case API_KEY -> required(rule.getApiKeyHash(), rule.getId(), "apiKeyHash");
            case PLAN -> required(rule.getPlan(), rule.getId(), "plan");
            case ENDPOINT -> {
                required(rule.getPathPattern(), rule.getId(), "pathPattern");
                if (rule.getPathPattern().length() > MAX_PATH_LENGTH || !rule.getPathPattern().startsWith("/")) {
                    fail(rule.getId() + ": pathPattern must start with / and have at most "
                            + MAX_PATH_LENGTH + " characters");
                }
                if (rule.getMethod() != null
                        && !HTTP_METHOD.matcher(rule.getMethod().toUpperCase()).matches()) {
                    fail(rule.getId() + ": unsupported HTTP method " + rule.getMethod());
                }
            }
            case GLOBAL -> { }
        }
    }

    private void required(String value, String id, String field) {
        if (value == null || value.isBlank()) fail(id + ": " + field + " is required for this scope");
    }

    private void fail(String message) {
        throw new IllegalArgumentException("Invalid rate-limit configuration: " + message);
    }
}
