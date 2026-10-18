package com.example.ratelimiter.controlplane;

import com.example.ratelimiter.config.RateLimiterProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-plane replica applies every version in order and swaps an immutable
 * snapshot. A gap is rejected: silently skipping configuration would make two
 * instances enforce different policies while reporting the same version.
 */
public class RuleReplica {
    private volatile Snapshot snapshot = new Snapshot(0, List.of());

    public synchronized Snapshot synchronize(ControlPlaneStore store) {
        Map<String, RateLimiterProperties.Rule> rules = new LinkedHashMap<>();
        snapshot.rules().forEach(rule -> rules.put(rule.getId(), copy(rule)));
        long expected = snapshot.version() + 1;
        for (var change : store.changesAfter(snapshot.version())) {
            if (change.version() != expected) {
                throw new IllegalStateException("Configuration version gap: expected " + expected
                        + " but received " + change.version());
            }
            rules.put(change.rule().getId(), copy(change.rule()));
            expected++;
        }
        snapshot = new Snapshot(expected - 1, List.copyOf(rules.values()));
        return snapshot;
    }

    public Snapshot snapshot() {
        return new Snapshot(snapshot.version(), snapshot.rules().stream().map(RuleReplica::copy).toList());
    }

    private static RateLimiterProperties.Rule copy(RateLimiterProperties.Rule source) {
        var target = new RateLimiterProperties.Rule();
        target.setId(source.getId()); target.setEnabled(source.isEnabled()); target.setType(source.getType());
        target.setPriority(source.getPriority()); target.setAlgorithm(source.getAlgorithm());
        target.setCapacity(source.getCapacity()); target.setRefillTokensPerSecond(source.getRefillTokensPerSecond());
        target.setCost(source.getCost()); target.setFailureStrategy(source.getFailureStrategy());
        target.setRegionMode(source.getRegionMode()); target.setEnforcementMode(source.getEnforcementMode());
        target.setTenantId(source.getTenantId());
        target.setUserId(source.getUserId()); target.setApiKeyHash(source.getApiKeyHash());
        target.setPlan(source.getPlan()); target.setMethod(source.getMethod());
        target.setPathPattern(source.getPathPattern());
        return target;
    }

    public record Snapshot(long version, List<RateLimiterProperties.Rule> rules) {}
}
