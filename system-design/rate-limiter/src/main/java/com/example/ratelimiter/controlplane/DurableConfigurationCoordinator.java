package com.example.ratelimiter.controlplane;

import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.ConfigVersionConflictException;
import com.example.ratelimiter.config.RateLimiterProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Connects the durable append-only control plane with the immutable local data
 * plane snapshot. Failed polling leaves the last-known-good snapshot active;
 * a version gap is rejected by RuleReplica rather than silently diverging.
 */
@Component
@Profile("durable")
public class DurableConfigurationCoordinator {
    private final ControlPlaneStore store;
    private final RuleReplica replica;
    private final DynamicConfigService local;
    private final AtomicLong lag = new AtomicLong();

    public DurableConfigurationCoordinator(ControlPlaneStore store, RuleReplica replica,
            DynamicConfigService local, MeterRegistry registry) {
        this.store = store;
        this.replica = replica;
        this.local = local;
        registry.gauge("rate_limiter.config.lag", lag);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initialize() {
        if (store.currentVersion() == 0) {
            long expected = 0;
            try {
                for (RateLimiterProperties.Rule rule : local.allRules()) {
                    expected = store.publish(rule, expected, "bootstrap");
                }
            } catch (ConfigVersionConflictException anotherInstanceWonBootstrap) {
                // Multiple fresh replicas may start together. PostgreSQL head
                // elects the bootstrap winner; losers simply consume its log.
            }
        }
        synchronize();
    }

    @Scheduled(fixedDelayString = "${rate-limiter.durable.config-poll-ms:1000}")
    public synchronized void synchronize() {
        RuleReplica.Snapshot snapshot = replica.synchronize(store);
        local.installSnapshot(snapshot.version(), snapshot.rules());
        lag.set(Math.max(0, store.currentVersion() - snapshot.version()));
    }

    public synchronized long upsert(RateLimiterProperties.Rule rule, long expectedVersion, String actor) {
        long version = store.publish(rule, expectedVersion, actor);
        synchronize();
        return version;
    }

    public synchronized boolean disable(String id, long expectedVersion, String actor) {
        RateLimiterProperties.Rule rule = local.findIncludingDisabled(id);
        if (rule == null) return false;
        rule.setEnabled(false); // durable tombstone keeps history and prevents resurrection
        store.publish(rule, expectedVersion, actor);
        synchronize();
        return true;
    }

    public synchronized long rollback(long sourceVersion, long expectedVersion, String actor) {
        long version = store.republishVersion(sourceVersion, expectedVersion, actor);
        synchronize();
        return version;
    }
}
