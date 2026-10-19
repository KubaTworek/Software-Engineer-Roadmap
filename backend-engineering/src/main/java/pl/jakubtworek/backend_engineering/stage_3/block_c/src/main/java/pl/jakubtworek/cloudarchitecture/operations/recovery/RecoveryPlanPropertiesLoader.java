package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Loads a version-controlled, provider-independent DR contract. */
public class RecoveryPlanPropertiesLoader {

    public DisasterRecoveryPlan load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);

        RecoveryObjective objective = new RecoveryObjective(
                duration(properties, "objective.rpo"),
                duration(properties, "objective.rto"));
        BackupPolicy databaseBackup = new BackupPolicy(
                CloudDependency.CLOUD_SQL,
                duration(properties, "backup.cloud-sql.interval"),
                duration(properties, "backup.cloud-sql.retention"),
                duration(properties, "backup.cloud-sql.restore-drill-interval"),
                bool(properties, "backup.cloud-sql.point-in-time-recovery"),
                bool(properties, "backup.cloud-sql.cross-region-copy"));

        return new DisasterRecoveryPlan(
                required(properties, "plan.name"),
                required(properties, "region.primary"),
                required(properties, "region.recovery"),
                objective,
                Map.of(CloudDependency.CLOUD_SQL, databaseBackup),
                duration(properties, "failover.traffic-switch-duration"),
                duration(properties, "failover.application-warmup-duration"),
                bool(properties, "infrastructure.reproducible"),
                bool(properties, "identity.workload-identity"),
                list(properties, "runbook.failover.steps"),
                list(properties, "runbook.rollback.steps"));
    }

    private Duration duration(Properties properties, String key) {
        try {
            return Duration.parse(required(properties, key));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid ISO-8601 duration for " + key, exception);
        }
    }

    private boolean bool(Properties properties, String key) {
        String value = required(properties, key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Expected boolean for " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private List<String> list(Properties properties, String key) {
        return Arrays.stream(required(properties, key).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }
}
