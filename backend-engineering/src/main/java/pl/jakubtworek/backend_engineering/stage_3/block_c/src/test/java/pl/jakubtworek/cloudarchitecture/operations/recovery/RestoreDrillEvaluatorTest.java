package pl.jakubtworek.cloudarchitecture.operations.recovery;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestoreDrillEvaluatorTest {

    private static final Instant INCIDENT = Instant.parse("2026-08-31T12:00:00Z");

    private final RestoreDrillEvaluator evaluator = new RestoreDrillEvaluator();

    @Test
    void acceptsFreshCrossRegionBackupWithVerifiedDataInsideRto() {
        BackupSnapshot backup = new BackupSnapshot(
                INCIDENT.minus(Duration.ofMinutes(4)), "europe-central2", "17", 12_500, "orders:abc");
        RestoreResult result = new RestoreResult(
                Duration.ofMinutes(18), "17", 12_500, "orders:abc", true);

        assertThat(evaluator.evaluate(plan(), backup, result, INCIDENT).successful()).isTrue();
    }

    @Test
    void backupExistingIsNotEnoughWhenRecoveryEvidenceBreaksRpoRtoOrIntegrity() {
        BackupSnapshot backup = new BackupSnapshot(
                INCIDENT.minus(Duration.ofMinutes(9)), "europe-west1", "17", 12_500, "orders:abc");
        RestoreResult result = new RestoreResult(
                Duration.ofMinutes(40), "16", 12_499, "orders:different", false);

        RestoreDrillReport report = evaluator.evaluate(plan(), backup, result, INCIDENT);

        assertThat(report.successful()).isFalse();
        assertThat(report.failures()).contains(
                "restored recovery point violates RPO",
                "restore duration violates RTO",
                "backup is not isolated from a primary-region disaster",
                "restored schema version differs from backup metadata",
                "record-count verification failed",
                "business-data checksum verification failed",
                "application smoke test against restored database failed");
    }

    private DisasterRecoveryPlan plan() {
        return new DisasterRecoveryPlan(
                "test", "europe-west1", "europe-central2",
                new RecoveryObjective(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Map.of(), Duration.ofMinutes(5), Duration.ofMinutes(10), true, true,
                List.of(), List.of());
    }
}
