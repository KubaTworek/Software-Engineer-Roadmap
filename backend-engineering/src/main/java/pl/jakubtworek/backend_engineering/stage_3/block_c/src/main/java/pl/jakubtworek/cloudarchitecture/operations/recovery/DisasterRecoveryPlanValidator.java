package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DisasterRecoveryPlanValidator {

    private static final Duration MAX_RESTORE_DRILL_INTERVAL = Duration.ofDays(90);

    public List<PlanViolation> validate(DisasterRecoveryPlan plan) {
        List<PlanViolation> violations = new ArrayList<>();

        requirePositive(plan.objective().rpo(), "RPO_NOT_POSITIVE", violations);
        requirePositive(plan.objective().rto(), "RTO_NOT_POSITIVE", violations);
        requirePositive(plan.trafficSwitchDuration(), "TRAFFIC_SWITCH_DURATION_NOT_POSITIVE", violations);
        requirePositive(plan.applicationWarmupDuration(), "APPLICATION_WARMUP_NOT_POSITIVE", violations);

        if (plan.primaryRegion().equals(plan.recoveryRegion())) {
            violations.add(new PlanViolation("SAME_RECOVERY_REGION",
                    "HA inside one region does not protect against a regional disaster"));
        }
        if (plan.estimatedFailoverDuration().compareTo(plan.objective().rto()) > 0) {
            violations.add(new PlanViolation("FAILOVER_EXCEEDS_RTO",
                    "Estimated traffic switch and warmup exceed the declared RTO"));
        }

        BackupPolicy database = plan.backupPolicies().get(CloudDependency.CLOUD_SQL);
        if (database == null) {
            violations.add(new PlanViolation("DATABASE_BACKUP_MISSING", "Cloud SQL requires a backup policy"));
        } else {
            validateDatabaseBackup(database, plan.objective(), violations);
        }

        if (!plan.infrastructureReproducible()) {
            violations.add(new PlanViolation("INFRASTRUCTURE_NOT_REPRODUCIBLE",
                    "Recovery infrastructure must be reconstructible from reviewed code"));
        }
        if (!plan.workloadIdentityEnabled()) {
            violations.add(new PlanViolation("WORKLOAD_IDENTITY_DISABLED",
                    "Workloads must not depend on exported long-lived service-account keys"));
        }

        requireStep(plan.failoverSteps(), "freeze-writes", "FAILOVER_FREEZE_WRITES_MISSING", violations);
        requireStep(plan.failoverSteps(), "promote-secondary", "FAILOVER_PROMOTION_MISSING", violations);
        requireStep(plan.failoverSteps(), "switch-traffic", "FAILOVER_TRAFFIC_SWITCH_MISSING", violations);
        requireStep(plan.failoverSteps(), "verify", "FAILOVER_VERIFICATION_MISSING", violations);
        requireStep(plan.rollbackSteps(), "rollback-image", "APPLICATION_ROLLBACK_MISSING", violations);
        requireStep(plan.rollbackSteps(), "verify", "ROLLBACK_VERIFICATION_MISSING", violations);

        return List.copyOf(violations);
    }

    private void validateDatabaseBackup(
            BackupPolicy policy, RecoveryObjective objective, List<PlanViolation> violations) {
        requirePositive(policy.backupInterval(), "BACKUP_INTERVAL_NOT_POSITIVE", violations);
        requirePositive(policy.retention(), "BACKUP_RETENTION_NOT_POSITIVE", violations);
        requirePositive(policy.restoreDrillInterval(), "RESTORE_DRILL_INTERVAL_NOT_POSITIVE", violations);
        if (policy.backupInterval().compareTo(objective.rpo()) > 0) {
            violations.add(new PlanViolation("BACKUP_INTERVAL_EXCEEDS_RPO",
                    "The maximum interval between recoverable points must not exceed RPO"));
        }
        if (!policy.pointInTimeRecovery()) {
            violations.add(new PlanViolation("PITR_DISABLED", "Point-in-time recovery is required for Cloud SQL"));
        }
        if (!policy.crossRegionCopy()) {
            violations.add(new PlanViolation("CROSS_REGION_BACKUP_MISSING",
                    "A regional incident must not make the backup unavailable"));
        }
        if (policy.retention().compareTo(policy.backupInterval()) < 0) {
            violations.add(new PlanViolation("RETENTION_TOO_SHORT", "Retention must cover at least one backup interval"));
        }
        if (policy.restoreDrillInterval().compareTo(MAX_RESTORE_DRILL_INTERVAL) > 0) {
            violations.add(new PlanViolation("RESTORE_DRILL_TOO_RARE",
                    "An untested backup is only a hypothesis; run a restore at least every 90 days"));
        }
    }

    private void requirePositive(Duration value, String code, List<PlanViolation> violations) {
        if (value.isZero() || value.isNegative()) {
            violations.add(new PlanViolation(code, "Recovery objective must be greater than zero"));
        }
    }

    private void requireStep(
            List<String> steps, String expected, String code, List<PlanViolation> violations) {
        boolean found = steps.stream()
                .map(step -> step.toLowerCase(Locale.ROOT))
                .anyMatch(step -> step.contains(expected));
        if (!found) {
            violations.add(new PlanViolation(code, "Runbook is missing step: " + expected));
        }
    }
}
