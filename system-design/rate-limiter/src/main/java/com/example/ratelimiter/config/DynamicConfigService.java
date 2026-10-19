package com.example.ratelimiter.config;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Local control-plane simulation. Readers receive an immutable snapshot, while
 * mutations use optimistic version checks. Persistence and cross-node delivery
 * remain an explicit production boundary rather than an implicit promise.
 */
@Service
public class DynamicConfigService {
    private static final int AUDIT_LIMIT = 1_000;
    private final RuleConfigurationValidator validator;
    private final AtomicLong version = new AtomicLong(1);
    private final ArrayDeque<RuleAuditEntry> audit = new ArrayDeque<>();
    private volatile List<RateLimiterProperties.Rule> snapshot;

    @Autowired
    public DynamicConfigService(RateLimiterProperties properties, RuleConfigurationValidator validator) {
        this.validator = validator;
        this.snapshot = immutable(properties.getRules());
    }

    /** Kept for small unit tests that construct the control plane directly. */
    public DynamicConfigService(RateLimiterProperties properties) {
        this(properties, new RuleConfigurationValidator(properties));
    }

    public List<RateLimiterProperties.Rule> allRules() {
        return snapshot.stream().filter(RateLimiterProperties.Rule::isEnabled).toList();
    }

    public long version() {
        return version.get();
    }

    public synchronized long upsertRule(RateLimiterProperties.Rule incoming, long expectedVersion, String actor) {
        requireVersion(expectedVersion);
        validator.validate(incoming);
        List<RateLimiterProperties.Rule> next = new ArrayList<>(snapshot);
        next.removeIf(rule -> rule.getId().equals(incoming.getId()));
        next.add(copy(incoming));
        snapshot = immutable(next);
        return record("UPSERT", incoming.getId(), actor);
    }

    public synchronized boolean disableRule(String id, long expectedVersion, String actor) {
        requireVersion(expectedVersion);
        boolean found = snapshot.stream().anyMatch(rule -> rule.getId().equals(id));
        if (!found) return false;
        List<RateLimiterProperties.Rule> next = snapshot.stream().map(rule -> {
            RateLimiterProperties.Rule copied = copy(rule);
            if (copied.getId().equals(id)) copied.setEnabled(false);
            return copied;
        }).toList();
        snapshot = immutable(next);
        record("DISABLE", id, actor);
        return true;
    }

    public synchronized List<RuleAuditEntry> audit() {
        return List.copyOf(audit);
    }

    /** Installs an already validated durable snapshot with one volatile swap. */
    public synchronized void installSnapshot(long durableVersion, List<RateLimiterProperties.Rule> rules) {
        if (durableVersion < version.get()) return;
        snapshot = immutable(rules);
        version.set(durableVersion);
    }

    /** Includes disabled rules so a durable tombstone can be built from the last definition. */
    public synchronized RateLimiterProperties.Rule findIncludingDisabled(String id) {
        return snapshot.stream().filter(rule -> rule.getId().equals(id))
                .findFirst().map(DynamicConfigService::copy).orElse(null);
    }

    private List<RateLimiterProperties.Rule> immutable(List<RateLimiterProperties.Rule> rules) {
        validator.validate(rules);
        return rules.stream().map(DynamicConfigService::copy)
                .sorted(Comparator.comparingInt(RateLimiterProperties.Rule::getPriority))
                .toList();
    }

    private void requireVersion(long expected) {
        if (expected != version.get()) {
            throw new ConfigVersionConflictException(expected, version.get());
        }
    }

    private long record(String action, String ruleId, String actor) {
        long newVersion = version.incrementAndGet();
        audit.addFirst(new RuleAuditEntry(newVersion, action, ruleId,
                actor == null || actor.isBlank() ? "unknown" : actor, Instant.now()));
        while (audit.size() > AUDIT_LIMIT) audit.removeLast();
        return newVersion;
    }

    private static RateLimiterProperties.Rule copy(RateLimiterProperties.Rule source) {
        RateLimiterProperties.Rule target = new RateLimiterProperties.Rule();
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

    public record RuleAuditEntry(long version, String action, String ruleId, String actor, Instant occurredAt) {}
}
