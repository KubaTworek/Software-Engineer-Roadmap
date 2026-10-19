package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Idempotent deletion workflow spanning primary data, cache, events and backup
 * restore suppression. Production implementations persist the workflow and use an outbox.
 */
public final class DataLifecycleCoordinator {

    private final TenantDataRepository repository;
    private final TenantCache cache;
    private final DeletionEventSink eventSink;
    private final BackupErasureRegistry backupRegistry;
    private final LifecycleAuditSink auditSink;
    private final Clock clock;
    private final Duration backupRetention;

    public DataLifecycleCoordinator(
            TenantDataRepository repository,
            TenantCache cache,
            DeletionEventSink eventSink,
            BackupErasureRegistry backupRegistry,
            LifecycleAuditSink auditSink,
            Clock clock,
            Duration backupRetention) {
        this.repository = Objects.requireNonNull(repository);
        this.cache = Objects.requireNonNull(cache);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.backupRegistry = Objects.requireNonNull(backupRegistry);
        this.auditSink = Objects.requireNonNull(auditSink);
        this.clock = Objects.requireNonNull(clock);
        if (backupRetention.isNegative() || backupRetention.isZero()) {
            throw new IllegalArgumentException("backupRetention must be positive");
        }
        this.backupRetention = backupRetention;
    }

    public DeletionResult erase(DeletionCommand command) {
        Instant now = Instant.now(clock);
        repository.anonymize(command.tenantId(), command.subjectId());
        cache.evict(new TenantCacheKey("subject", command.tenantId(), command.subjectId()));
        eventSink.publish(new PersonalDataErased(
                command.deletionId(), command.tenantId(), command.subjectId(), now));

        // Immutable backups normally expire instead of being edited in place. The tombstone
        // prevents erased data from becoming live again after a restore.
        backupRegistry.register(new BackupErasureTombstone(
                command.deletionId(), command.tenantId(), command.subjectId(), now.plus(backupRetention)));
        auditSink.append(new LifecycleAuditEvent(
                command.deletionId(), command.tenantId(), command.requestedBy(), "personal-data.erased", now));
        return new DeletionResult(command.deletionId(), now, now.plus(backupRetention));
    }

    public record DeletionCommand(UUID deletionId, TenantId tenantId, String subjectId, String requestedBy) {
        public DeletionCommand {
            Objects.requireNonNull(deletionId);
            Objects.requireNonNull(tenantId);
            requireText(subjectId, "subjectId");
            requireText(requestedBy, "requestedBy");
        }
    }

    public record DeletionResult(UUID deletionId, Instant completedAt, Instant backupExpiresAt) {
    }

    public record PersonalDataErased(
            UUID deletionId, TenantId tenantId, String subjectReference, Instant occurredAt) {
    }

    public record BackupErasureTombstone(
            UUID deletionId, TenantId tenantId, String subjectReference, Instant retainUntil) {
    }

    public record LifecycleAuditEvent(
            UUID deletionId, TenantId tenantId, String actorId, String action, Instant occurredAt) {
    }

    public interface TenantCache {
        void evict(TenantCacheKey key);
    }

    public interface DeletionEventSink {
        void publish(PersonalDataErased event);
    }

    public interface BackupErasureRegistry {
        void register(BackupErasureTombstone tombstone);
    }

    public interface LifecycleAuditSink {
        void append(LifecycleAuditEvent event);
    }

    /** Test adapter demonstrating idempotency by deletion id. */
    public static final class InMemoryLifecycleSinks
            implements TenantCache, DeletionEventSink, BackupErasureRegistry, LifecycleAuditSink {

        private final Set<TenantCacheKey> evictedKeys = new HashSet<>();
        private final Set<UUID> published = new HashSet<>();
        private final Set<UUID> registeredTombstones = new HashSet<>();
        private final Set<UUID> audited = new HashSet<>();
        private final List<PersonalDataErased> events = new ArrayList<>();
        private final List<BackupErasureTombstone> tombstones = new ArrayList<>();
        private final List<LifecycleAuditEvent> auditEvents = new ArrayList<>();

        @Override
        public synchronized void evict(TenantCacheKey key) {
            evictedKeys.add(key);
        }

        @Override
        public synchronized void publish(PersonalDataErased event) {
            if (published.add(event.deletionId())) {
                events.add(event);
            }
        }

        @Override
        public synchronized void register(BackupErasureTombstone tombstone) {
            if (registeredTombstones.add(tombstone.deletionId())) {
                tombstones.add(tombstone);
            }
        }

        @Override
        public synchronized void append(LifecycleAuditEvent event) {
            if (audited.add(event.deletionId())) {
                auditEvents.add(event);
            }
        }

        public Set<TenantCacheKey> evictedKeys() {
            return Set.copyOf(evictedKeys);
        }

        public List<PersonalDataErased> events() {
            return List.copyOf(events);
        }

        public List<BackupErasureTombstone> tombstones() {
            return List.copyOf(tombstones);
        }

        public List<LifecycleAuditEvent> auditEvents() {
            return List.copyOf(auditEvents);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
