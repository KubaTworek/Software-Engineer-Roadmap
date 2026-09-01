package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DisasterRecoveryPlan(
        String name,
        String primaryRegion,
        String recoveryRegion,
        RecoveryObjective objective,
        Map<CloudDependency, BackupPolicy> backupPolicies,
        Duration trafficSwitchDuration,
        Duration applicationWarmupDuration,
        boolean infrastructureReproducible,
        boolean workloadIdentityEnabled,
        List<String> failoverSteps,
        List<String> rollbackSteps) {

    public DisasterRecoveryPlan {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(primaryRegion, "primaryRegion");
        Objects.requireNonNull(recoveryRegion, "recoveryRegion");
        Objects.requireNonNull(objective, "objective");
        backupPolicies = Map.copyOf(backupPolicies);
        Objects.requireNonNull(trafficSwitchDuration, "trafficSwitchDuration");
        Objects.requireNonNull(applicationWarmupDuration, "applicationWarmupDuration");
        failoverSteps = List.copyOf(failoverSteps);
        rollbackSteps = List.copyOf(rollbackSteps);
    }

    public Duration estimatedFailoverDuration() {
        return trafficSwitchDuration.plus(applicationWarmupDuration);
    }
}
