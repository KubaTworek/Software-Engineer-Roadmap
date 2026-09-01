package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.util.Objects;

public record BackupPolicy(
        CloudDependency resource,
        Duration backupInterval,
        Duration retention,
        Duration restoreDrillInterval,
        boolean pointInTimeRecovery,
        boolean crossRegionCopy) {

    public BackupPolicy {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(backupInterval, "backupInterval");
        Objects.requireNonNull(retention, "retention");
        Objects.requireNonNull(restoreDrillInterval, "restoreDrillInterval");
    }
}
