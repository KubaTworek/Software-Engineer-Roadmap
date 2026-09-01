package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Turns "backup enabled" into measurable evidence that data can actually be restored. */
public class RestoreDrillEvaluator {

    public RestoreDrillReport evaluate(
            DisasterRecoveryPlan plan,
            BackupSnapshot backup,
            RestoreResult restore,
            Instant incidentTime) {
        List<String> failures = new ArrayList<>();

        Duration recoveryPointAge = Duration.between(backup.createdAt(), incidentTime);
        if (recoveryPointAge.isNegative() || recoveryPointAge.compareTo(plan.objective().rpo()) > 0) {
            failures.add("restored recovery point violates RPO");
        }
        if (restore.duration().compareTo(plan.objective().rto()) > 0) {
            failures.add("restore duration violates RTO");
        }
        if (backup.region().equals(plan.primaryRegion())) {
            failures.add("backup is not isolated from a primary-region disaster");
        }
        if (!backup.schemaVersion().equals(restore.schemaVersion())) {
            failures.add("restored schema version differs from backup metadata");
        }
        if (backup.recordCount() != restore.recordCount()) {
            failures.add("record-count verification failed");
        }
        if (!backup.checksum().equals(restore.checksum())) {
            failures.add("business-data checksum verification failed");
        }
        if (!restore.applicationSmokeTestPassed()) {
            failures.add("application smoke test against restored database failed");
        }

        return new RestoreDrillReport(failures.isEmpty(), failures);
    }
}
