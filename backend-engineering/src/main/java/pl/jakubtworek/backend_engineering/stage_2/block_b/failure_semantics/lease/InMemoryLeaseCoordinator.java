package pl.jakubtworek.backend_engineering.stage_2.block_b.failure_semantics.lease;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Local teaching model. A production coordinator must allocate tokens atomically
 * in durable, linearizable storage shared by all application instances.
 */
public final class InMemoryLeaseCoordinator {

    private final Clock clock;
    private final Map<String, Long> lastTokens = new HashMap<>();
    private final Map<String, LeaseGrant> activeLeases = new HashMap<>();

    public InMemoryLeaseCoordinator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized LeaseGrant acquire(String resourceId, String ownerId, Duration ttl) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        LeaseGrant activeLease = activeLeases.get(resourceId);
        if (activeLease != null && !activeLease.isExpired(clock)) {
            throw new LeaseUnavailableException(resourceId, activeLease.ownerId());
        }

        long token = Math.incrementExact(lastTokens.getOrDefault(resourceId, 0L));
        lastTokens.put(resourceId, token);
        LeaseGrant grant = new LeaseGrant(resourceId, ownerId, token, clock.instant().plus(ttl));
        activeLeases.put(resourceId, grant);
        return grant;
    }
}
