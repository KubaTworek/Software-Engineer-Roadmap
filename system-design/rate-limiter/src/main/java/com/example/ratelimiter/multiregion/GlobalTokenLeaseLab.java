package com.example.ratelimiter.multiregion;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic model of a global allocator leasing token batches to regions.
 * The global database is not contacted for every request. Availability rises,
 * while the maximum unreported exposure equals tokens in outstanding leases.
 */
public class GlobalTokenLeaseLab {
    private long unleased;
    private long fencingToken;
    private final Map<String, Lease> leases = new HashMap<>();

    public GlobalTokenLeaseLab(long globalBudget) {
        if (globalBudget < 0) throw new IllegalArgumentException("budget must be non-negative");
        this.unleased = globalBudget;
    }

    public synchronized Lease grant(String region, long requested, Instant now, Instant expiresAt) {
        if (requested <= 0 || !expiresAt.isAfter(now)) throw new IllegalArgumentException("invalid lease");
        Lease previous = leases.get(region);
        if (previous != null && now.isBefore(previous.expiresAt())) {
            throw new IllegalStateException("region already owns an active lease");
        }
        if (previous != null) {
            unleased += previous.remaining();
            leases.remove(region);
        }
        long amount = Math.min(requested, unleased);
        unleased -= amount;
        Lease lease = new Lease(region, ++fencingToken, amount, amount, expiresAt);
        leases.put(region, lease);
        return lease;
    }

    public synchronized boolean consume(String region, long fence, long cost, Instant now) {
        Lease lease = leases.get(region);
        if (lease == null || lease.fencingToken() != fence || !now.isBefore(lease.expiresAt())
                || cost <= 0 || lease.remaining() < cost) return false;
        leases.put(region, new Lease(region, fence, lease.granted(), lease.remaining() - cost, lease.expiresAt()));
        return true;
    }

    /**
     * Reclaims only unused tokens and increments fencing on the next grant.
     * A delayed request from the old owner is rejected even if its wall-clock
     * lease looked valid when it was sent.
     */
    public synchronized void expire(String region, Instant now) {
        Lease lease = leases.get(region);
        if (lease != null && !now.isBefore(lease.expiresAt())) {
            unleased += lease.remaining();
            leases.remove(region);
        }
    }

    public synchronized long unleased() { return unleased; }
    public synchronized long outstandingExposure() {
        return leases.values().stream().mapToLong(Lease::remaining).sum();
    }

    public record Lease(String region, long fencingToken, long granted, long remaining, Instant expiresAt) {}
}
