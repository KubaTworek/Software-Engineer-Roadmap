package com.example.ratelimiter.controlplane;

import com.example.ratelimiter.config.RateLimiterProperties;
import java.time.Instant;
import java.util.List;

/** Trwałe źródło zmian konfiguracji; data plane konsumuje wersjonowany log. */
public interface ControlPlaneStore {
    long publish(RateLimiterProperties.Rule rule, long expectedVersion, String actor);
    List<RuleChange> changesAfter(long version);
    long currentVersion();
    long republishVersion(long sourceVersion, long expectedVersion, String actor);

    record RuleChange(long version, RateLimiterProperties.Rule rule, String actor, Instant occurredAt) {}
}
