package pl.jakubtworek.cloudarchitecture.operations.recovery;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisasterRecoveryPlanValidatorTest {

    private final DisasterRecoveryPlanValidator validator = new DisasterRecoveryPlanValidator();

    @Test
    void referenceConfigurationMeetsDeclaredRecoveryContract() throws Exception {
        DisasterRecoveryPlan plan;
        try (InputStream input = getClass().getResourceAsStream("/operations/reference-dr-plan.properties")) {
            assertThat(input).isNotNull();
            plan = new RecoveryPlanPropertiesLoader().load(input);
        }

        assertThat(plan.objective()).isEqualTo(new RecoveryObjective(Duration.ofMinutes(5), Duration.ofMinutes(30)));
        assertThat(plan.estimatedFailoverDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(validator.validate(plan)).isEmpty();
    }

    @Test
    void detectsPlanThatConfusesRegionalHaWithDisasterRecovery() {
        DisasterRecoveryPlan invalid = new DisasterRecoveryPlan(
                "invalid",
                "europe-west1",
                "europe-west1",
                new RecoveryObjective(Duration.ofMinutes(5), Duration.ofMinutes(10)),
                Map.of(CloudDependency.CLOUD_SQL, new BackupPolicy(
                        CloudDependency.CLOUD_SQL,
                        Duration.ofHours(1),
                        Duration.ofMinutes(30),
                        Duration.ofDays(120),
                        false,
                        false)),
                Duration.ofMinutes(8),
                Duration.ofMinutes(8),
                false,
                false,
                List.of("switch-traffic"),
                List.of());

        assertThat(validator.validate(invalid))
                .extracting(PlanViolation::code)
                .contains(
                        "SAME_RECOVERY_REGION",
                        "FAILOVER_EXCEEDS_RTO",
                        "BACKUP_INTERVAL_EXCEEDS_RPO",
                        "PITR_DISABLED",
                        "CROSS_REGION_BACKUP_MISSING",
                        "RESTORE_DRILL_TOO_RARE",
                        "INFRASTRUCTURE_NOT_REPRODUCIBLE",
                        "WORKLOAD_IDENTITY_DISABLED",
                        "FAILOVER_PROMOTION_MISSING",
                        "APPLICATION_ROLLBACK_MISSING");
    }

    @Test
    void configurationLoaderFailsFastWhenRequiredRecoveryObjectiveIsMissing() {
        InputStream incomplete = new ByteArrayInputStream(
                "plan.name=incomplete".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new RecoveryPlanPropertiesLoader().load(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objective.rpo");
    }
}
