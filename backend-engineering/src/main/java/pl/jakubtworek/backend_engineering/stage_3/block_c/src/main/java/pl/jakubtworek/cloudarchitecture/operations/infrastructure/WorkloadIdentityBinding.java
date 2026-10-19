package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import java.util.Set;

public record WorkloadIdentityBinding(
        String workload,
        String serviceAccount,
        boolean exportedStaticKey,
        Set<String> requiredPermissions,
        Set<String> grantedPermissions) {

    public WorkloadIdentityBinding {
        requiredPermissions = Set.copyOf(requiredPermissions);
        grantedPermissions = Set.copyOf(grantedPermissions);
    }
}
