package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataLifecycleCoordinatorTest {

    @Test
    void erasureMustPropagateToPrimaryDataCacheEventsBackupRestoreAndAudit() {
        TenantId tenant = new TenantId("alpha-co");
        TenantDataRepository repository = new TenantDataRepository();
        repository.save(new TenantDataRecord(tenant, "customer-1", "alice@example.com", "Alice"));
        DataLifecycleCoordinator.InMemoryLifecycleSinks sinks =
                new DataLifecycleCoordinator.InMemoryLifecycleSinks();
        DataLifecycleCoordinator coordinator = coordinator(repository, sinks);
        UUID deletionId = UUID.fromString("d6b5812e-5401-4eb9-8b72-286abc31c14f");

        DataLifecycleCoordinator.DeletionResult result = coordinator.erase(
                new DataLifecycleCoordinator.DeletionCommand(
                        deletionId, tenant, "customer-1", "privacy-operator"));

        assertThat(repository.find(tenant, "customer-1")).get().satisfies(record -> {
            assertThat(record.email()).isEqualTo("erased@example.invalid");
            assertThat(record.displayName()).isEqualTo("ERASED");
        });
        assertThat(sinks.evictedKeys()).containsExactly(
                new TenantCacheKey("subject", tenant, "customer-1"));
        assertThat(sinks.events()).singleElement().satisfies(event -> {
            assertThat(event.deletionId()).isEqualTo(deletionId);
            assertThat(event.toString()).doesNotContain("alice@example.com", "Alice");
        });
        assertThat(sinks.tombstones()).singleElement().satisfies(tombstone ->
                assertThat(tombstone.retainUntil()).isEqualTo(Instant.parse("2026-02-14T10:00:00Z")));
        assertThat(sinks.auditEvents()).singleElement().satisfies(event ->
                assertThat(event.actorId()).isEqualTo("privacy-operator"));
        assertThat(result.backupExpiresAt()).isEqualTo(Instant.parse("2026-02-14T10:00:00Z"));
    }

    @Test
    void retryingSameDeletionMustNotDuplicateExternalEffects() {
        TenantId tenant = new TenantId("alpha-co");
        TenantDataRepository repository = new TenantDataRepository();
        repository.save(new TenantDataRecord(tenant, "customer-1", "alice@example.com", "Alice"));
        DataLifecycleCoordinator.InMemoryLifecycleSinks sinks =
                new DataLifecycleCoordinator.InMemoryLifecycleSinks();
        DataLifecycleCoordinator coordinator = coordinator(repository, sinks);
        DataLifecycleCoordinator.DeletionCommand command = new DataLifecycleCoordinator.DeletionCommand(
                UUID.fromString("d6b5812e-5401-4eb9-8b72-286abc31c14f"),
                tenant,
                "customer-1",
                "privacy-operator");

        coordinator.erase(command);
        coordinator.erase(command);

        assertThat(sinks.events()).hasSize(1);
        assertThat(sinks.tombstones()).hasSize(1);
        assertThat(sinks.auditEvents()).hasSize(1);
        assertThat(sinks.evictedKeys()).hasSize(1);
    }

    private static DataLifecycleCoordinator coordinator(
            TenantDataRepository repository,
            DataLifecycleCoordinator.InMemoryLifecycleSinks sinks) {
        return new DataLifecycleCoordinator(
                repository,
                sinks,
                sinks,
                sinks,
                sinks,
                Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofDays(30));
    }
}
