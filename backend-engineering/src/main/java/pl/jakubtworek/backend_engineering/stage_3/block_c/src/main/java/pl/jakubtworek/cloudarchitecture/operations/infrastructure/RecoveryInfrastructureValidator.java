package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import pl.jakubtworek.cloudarchitecture.operations.recovery.DisasterRecoveryPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Checks that declared recovery objectives have corresponding desired infrastructure. */
public class RecoveryInfrastructureValidator {

    public List<ArchitectureViolation> validate(
            DisasterRecoveryPlan plan, Collection<InfrastructureResource> infrastructure) {
        List<ArchitectureViolation> violations = new ArrayList<>();

        InfrastructureResource primary = find(infrastructure, "cloud_sql.orders_primary");
        if (primary == null) {
            violations.add(new ArchitectureViolation("PRIMARY_DATABASE_MISSING", "Cloud SQL primary is absent"));
        } else {
            requireAttribute(primary, "region", plan.primaryRegion(), "PRIMARY_REGION_MISMATCH", violations);
            requireAttribute(primary, "ha", "regional", "PRIMARY_HA_DISABLED", violations);
            requireAttribute(primary, "pitr", "true", "PRIMARY_PITR_DISABLED", violations);
        }

        InfrastructureResource recovery = find(infrastructure, "cloud_sql.orders_dr");
        if (recovery == null) {
            violations.add(new ArchitectureViolation(
                    "RECOVERY_DATABASE_MISSING", "RTO assumes a pre-provisioned recovery database"));
        } else {
            requireAttribute(recovery, "region", plan.recoveryRegion(), "RECOVERY_REGION_MISMATCH", violations);
            requireAttribute(
                    recovery, "source", "cloud_sql.orders_primary", "RECOVERY_SOURCE_MISMATCH", violations);
        }

        return List.copyOf(violations);
    }

    private InfrastructureResource find(Collection<InfrastructureResource> resources, String address) {
        return resources.stream()
                .filter(resource -> resource.address().equals(address))
                .findFirst()
                .orElse(null);
    }

    private void requireAttribute(
            InfrastructureResource resource,
            String attribute,
            String expected,
            String code,
            List<ArchitectureViolation> violations) {
        String actual = resource.managedAttributes().get(attribute);
        if (!expected.equals(actual)) {
            violations.add(new ArchitectureViolation(
                    code, resource.address() + "." + attribute + " expected=" + expected + " actual=" + actual));
        }
    }
}
