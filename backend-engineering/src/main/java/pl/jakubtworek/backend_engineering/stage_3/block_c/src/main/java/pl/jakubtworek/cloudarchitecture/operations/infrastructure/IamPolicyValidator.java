package pl.jakubtworek.cloudarchitecture.operations.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IamPolicyValidator {

    public List<IamViolation> validate(Collection<WorkloadIdentityBinding> bindings) {
        List<IamViolation> violations = new ArrayList<>();
        Map<String, List<String>> workloadsByServiceAccount = new HashMap<>();

        for (WorkloadIdentityBinding binding : bindings) {
            workloadsByServiceAccount
                    .computeIfAbsent(binding.serviceAccount(), ignored -> new ArrayList<>())
                    .add(binding.workload());

            if (binding.exportedStaticKey()) {
                violations.add(new IamViolation(
                        "STATIC_SERVICE_ACCOUNT_KEY", binding.workload(),
                        "Use workload identity and short-lived credentials"));
            }

            Set<String> missing = difference(binding.requiredPermissions(), binding.grantedPermissions());
            if (!missing.isEmpty()) {
                violations.add(new IamViolation(
                        "MISSING_PERMISSION", binding.workload(), missing.toString()));
            }

            Set<String> excessive = difference(binding.grantedPermissions(), binding.requiredPermissions());
            if (!excessive.isEmpty()) {
                violations.add(new IamViolation(
                        "EXCESSIVE_PERMISSION", binding.workload(), excessive.toString()));
            }
        }

        workloadsByServiceAccount.forEach((serviceAccount, workloads) -> {
            if (workloads.size() > 1) {
                violations.add(new IamViolation(
                        "SHARED_SERVICE_ACCOUNT", String.join(",", workloads), serviceAccount));
            }
        });

        return List.copyOf(violations);
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
