package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.cloudarchitecture.operations.ReferenceOperationalArchitecture;
import pl.jakubtworek.cloudarchitecture.operations.recovery.BackupPolicy;
import pl.jakubtworek.cloudarchitecture.operations.recovery.CloudDependency;
import pl.jakubtworek.cloudarchitecture.operations.recovery.DisasterRecoveryPlan;
import pl.jakubtworek.cloudarchitecture.operations.recovery.RecoveryObjective;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureGovernanceTest {

    private final InfrastructureDriftDetector driftDetector = new InfrastructureDriftDetector();
    private final IamPolicyValidator iamValidator = new IamPolicyValidator();
    private final RecoveryInfrastructureValidator recoveryInfrastructureValidator =
            new RecoveryInfrastructureValidator();

    @Test
    void identicalDesiredAndObservedInfrastructureHasNoDrift() {
        List<InfrastructureResource> desired = ReferenceOperationalArchitecture.desiredInfrastructure();

        assertThat(driftDetector.detect(desired, desired)).isEmpty();
    }

    @Test
    void detectsConsoleChangeMissingRecoveryDatabaseAndUnmanagedResource() {
        List<InfrastructureResource> desired = ReferenceOperationalArchitecture.desiredInfrastructure();
        List<InfrastructureResource> actual = new ArrayList<>(desired);
        actual.removeIf(resource -> resource.address().equals("cloud_sql.orders_dr"));
        actual.removeIf(resource -> resource.address().equals("cloud_run.orders_api"));
        actual.add(new InfrastructureResource("cloud_run.orders_api", "cloud-run-service", Map.of(
                "region", "europe-west1",
                "serviceAccount", "default",
                "ingress", "all")));
        actual.add(new InfrastructureResource("storage.manual_backup", "bucket", Map.of("owner", "console")));

        assertThat(driftDetector.detect(desired, actual))
                .extracting(InfrastructureDrift::type)
                .contains(
                        DriftType.MISSING_RESOURCE,
                        DriftType.CHANGED_ATTRIBUTE,
                        DriftType.UNEXPECTED_RESOURCE);
    }

    @Test
    void referenceWorkloadsUseDedicatedKeylessLeastPrivilegeIdentities() {
        assertThat(iamValidator.validate(ReferenceOperationalArchitecture.leastPrivilegeBindings())).isEmpty();
    }

    @Test
    void desiredInfrastructureActuallySupportsRegionsDeclaredByRecoveryPlan() {
        assertThat(recoveryInfrastructureValidator.validate(
                recoveryPlan(), ReferenceOperationalArchitecture.desiredInfrastructure())).isEmpty();
    }

    @Test
    void rtoCannotRelyOnRecoveryDatabaseThatExistsOnlyInTheRunbook() {
        List<InfrastructureResource> withoutRecoveryDatabase = ReferenceOperationalArchitecture
                .desiredInfrastructure().stream()
                .filter(resource -> !resource.address().equals("cloud_sql.orders_dr"))
                .toList();

        assertThat(recoveryInfrastructureValidator.validate(recoveryPlan(), withoutRecoveryDatabase))
                .extracting(ArchitectureViolation::code)
                .containsExactly("RECOVERY_DATABASE_MISSING");
    }

    @Test
    void rejectsStaticSharedIdentityWithMissingAndExcessPermissions() {
        WorkloadIdentityBinding api = new WorkloadIdentityBinding(
                "orders-api",
                "default@project.iam",
                true,
                Set.of("cloudsql.instances.connect"),
                Set.of("resourcemanager.projects.setIamPolicy"));
        WorkloadIdentityBinding worker = new WorkloadIdentityBinding(
                "order-worker",
                "default@project.iam",
                false,
                Set.of("pubsub.subscriptions.consume"),
                Set.of("pubsub.subscriptions.consume"));

        assertThat(iamValidator.validate(List.of(api, worker)))
                .extracting(IamViolation::code)
                .contains(
                        "STATIC_SERVICE_ACCOUNT_KEY",
                        "MISSING_PERMISSION",
                        "EXCESSIVE_PERMISSION",
                        "SHARED_SERVICE_ACCOUNT");
    }

    private DisasterRecoveryPlan recoveryPlan() {
        return new DisasterRecoveryPlan(
                "orders", "europe-west1", "europe-central2",
                new RecoveryObjective(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                Map.of(CloudDependency.CLOUD_SQL, new BackupPolicy(
                        CloudDependency.CLOUD_SQL,
                        Duration.ofMinutes(5),
                        Duration.ofDays(35),
                        Duration.ofDays(30),
                        true,
                        true)),
                Duration.ofMinutes(5), Duration.ofMinutes(10), true, true,
                List.of("freeze-writes", "promote-secondary", "switch-traffic", "verify"),
                List.of("rollback-image", "verify"));
    }
}
